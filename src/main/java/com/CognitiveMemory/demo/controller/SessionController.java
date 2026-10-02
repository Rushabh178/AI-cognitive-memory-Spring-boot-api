package com.CognitiveMemory.demo.controller;

import com.CognitiveMemory.demo.dto.CreateSessionRequest;
import com.CognitiveMemory.demo.dto.SessionCreateResponse;
import com.CognitiveMemory.demo.dto.SessionSummaryResponse;
import com.CognitiveMemory.demo.entity.User;
import com.CognitiveMemory.demo.gateway.PythonAiGateway;
import com.CognitiveMemory.demo.sessions.Repository.ChatMessageRepository;
import com.CognitiveMemory.demo.sessions.Repository.ChatSessionRepository;
import com.CognitiveMemory.demo.sessions.entity.ChatMessage;
import com.CognitiveMemory.demo.sessions.entity.ChatSession;
import com.CognitiveMemory.demo.sessions.service.CurrentUserService;
import com.CognitiveMemory.demo.utils.HistoryQuestionUtil;
import com.CognitiveMemory.demo.utils.MemoryRelevanceUtil;
import com.CognitiveMemory.demo.utils.MemoryWorthinessUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final CurrentUserService currentUserService;
    private final PythonAiGateway pythonAiGateway;

    public SessionController(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            CurrentUserService currentUserService,
            PythonAiGateway pythonAiGateway
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.currentUserService = currentUserService;
        this.pythonAiGateway = pythonAiGateway;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateSessionRequest request) {
        try {
            User user = currentUserService.requireUser();
            String title = (request == null || request.getTitle() == null || request.getTitle().isBlank())
                    ? "New chat"
                    : request.getTitle().trim();

            ChatSession session = ChatSession.builder()
                    .user(user)
                    .title(title)
                    .createdAt(Instant.now())
                    .build();

            return ResponseEntity.ok(sessionRepository.save(session));
        } catch (Exception e) {
            log.error("Create session failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create session");
        }
    }

    @GetMapping
    public ResponseEntity<?> listMine() {
        try {
            User user = currentUserService.requireUser();
            return ResponseEntity.ok(sessionRepository.findByUserOrderByCreatedAtDesc(user));
        } catch (Exception e) {
            log.error("List sessions failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to list sessions");
        }
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<?> getMessages(@PathVariable Long sessionId) {
        try {
            User user = currentUserService.requireUser();
            Optional<ChatSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty() || !sessionOpt.get().getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Session not found");
            }

            List<ChatMessage> messages = messageRepository.findTop50BySessionOrderByCreatedAtDesc(sessionOpt.get());
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Get messages failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to load messages");
        }
    }

    @PostMapping("/{sessionId}/message")
    public ResponseEntity<?> postUserMessage(
            @PathVariable Long sessionId,
            @RequestBody(required = false) Map<String, Object> rawBody
    ) {
        try {
            User user = currentUserService.requireUser();
            Optional<ChatSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty() || !sessionOpt.get().getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Session not found");
            }

            Object messageValue = rawBody == null ? null : rawBody.get("message");
            String message = messageValue instanceof String s ? s : null;
            if (message == null || message.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildBadMessageError(rawBody));
            }

            ChatSession session = sessionOpt.get();
            String userId = String.valueOf(user.getId());

            // Step 0: Snapshot the CURRENT session's prior turns BEFORE saving this new
            // message, so it naturally excludes the message we're about to save (no
            // off-by-one filtering needed). findTop10...Desc returns newest-first; reverse
            // to chronological order since that's the order real conversation turns need
            // to appear in the LLM's messages array.
            List<ChatMessage> priorMessages = messageRepository.findTop10BySessionOrderByCreatedAtDesc(session);
            List<Map<String, String>> sessionHistory = priorMessages.stream()
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                    .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                    .collect(Collectors.toList());

            // Step 1: Save user message
            ChatMessage userMsg = messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role("user")
                    .content(message.trim())
                    .createdAt(Instant.now())
                    .build());

            // Step 2: Retrieve relevant memories BEFORE the current message is stored, so
            // only genuinely PAST memories are ever returned as context — skipped for
            // context-independent queries to avoid leaking unrelated personal context
            boolean memoryRelevant = MemoryRelevanceUtil.isMemoryRelevant(userMsg.getContent());
            log.info("Memory relevance for session={}: {}", session.getId(), memoryRelevant);
            List<String> memories = memoryRelevant
                    ? pythonAiGateway.retrieveMemory(userId, userMsg.getContent(), 5)
                    : List.of();

            // Step 2b: Graph relationship context and the rolling summary (Task 1) — both
            // are supplementary long-term-memory layers, same as ChromaDB memories above.
            // Both gateway methods already swallow their own failures and return "" so a
            // down Python graph/summary path never breaks chat.
            String graphContext = memoryRelevant
                    ? pythonAiGateway.getGraphContext(userId, userMsg.getContent(), 5)
                    : "";
            String summary = pythonAiGateway.getLatestSummary(userId);

            // Step 2c: Revision history, only for history-flavoured messages ("who did I use to
            // like before that?"). The graph context and vector retrieval above are current-state
            // only, so without this a replaced fact is invisible to the model. Bounded on the
            // Python side (at most 5 changed facts, scoped to the query's domain). Not gated on
            // memoryRelevant: a history question is itself the signal. Returns "" on failure.
            boolean historyQuestion = HistoryQuestionUtil.isHistoryQuestion(userMsg.getContent());
            String history = historyQuestion
                    ? pythonAiGateway.getGraphHistory(userId, userMsg.getContent())
                    : "";

            // Step 3: Build the combined LONG-TERM context string, excluding near-duplicates
            // of the current message. Each source gets its own labeled section so the LLM
            // (and anyone reading the logs) can tell them apart — priority-instruction
            // wording that ties this to the current session lives in ai_service.py's system
            // prompt, not here; this is just assembly.
            List<String> filteredMemories = MemoryWorthinessUtil.filterNearDuplicates(userMsg.getContent(), memories);
            StringBuilder contextBuilder = new StringBuilder();
            if (!filteredMemories.isEmpty()) {
                contextBuilder.append("Related past memories:\n")
                        .append(String.join("\n", filteredMemories)).append("\n\n");
            }
            if (!graphContext.isBlank()) {
                contextBuilder.append(graphContext).append("\n\n");
            }
            if (!history.isBlank()) {
                contextBuilder.append("Historical context (previous values the user has since replaced — ")
                        .append("NOT current; use only to answer questions about the past):\n")
                        .append(history).append("\n\n");
            }
            if (!summary.isBlank()) {
                contextBuilder.append("User summary (periodically generated):\n")
                        .append(summary).append("\n\n");
            }
            String context = contextBuilder.toString().trim();
            log.info("Context built for session={} ({} chars: {} memories, graph={}, history={} (question={}), summary={}), sessionHistory={} turns",
                    session.getId(), context.length(), filteredMemories.size(),
                    !graphContext.isBlank(), !history.isBlank(), historyQuestion,
                    !summary.isBlank(), sessionHistory.size());

            // Step 4: Call the AI with message + long-term context + current-session history
            String answer = pythonAiGateway.sendToAi(userId, userMsg.getContent(), context, sessionHistory);

            // Step 5: Store the user message as memory now that the response has been
            // generated, so it becomes context for FUTURE conversations only — and only
            // if it is actually memory-worthy. A store failure must never fail the request.
            if (MemoryWorthinessUtil.isMemoryWorthy(userMsg.getContent())) {
                try {
                    pythonAiGateway.storeMemory(userId, userMsg.getContent(), "user");
                    log.info("User message stored as memory for session={}", session.getId());
                } catch (Exception ex) {
                    log.warn("Failed to store user memory, continuing: {}", ex.getMessage());
                }
            } else {
                log.info("Message not memory-worthy — skipping storage (session={})", session.getId());
            }

            // Step 6: Save AI response
            ChatMessage assistantMsg = messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role("assistant")
                    .content(answer)
                    .createdAt(Instant.now())
                    .build());

            // Also store the AI response as memory if substantial — responses under 20
            // words are usually too short to be meaningful memories. Never fail the
            // response just because assistant memory storage failed.
            if (answer != null && answer.split("\\s+").length > 20) {
                try {
                    pythonAiGateway.storeMemory(userId, answer, "assistant");
                    log.info("AI response stored as memory for session={}", session.getId());
                } catch (Exception ex) {
                    log.warn("Failed to store assistant memory: {}", ex.getMessage());
                }
            }

            return ResponseEntity.ok(assistantMsg);
        } catch (Exception e) {
            log.error("Post message failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send message");
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createSession(@Valid @RequestBody CreateSessionRequest request) {
        try {
            User user = currentUserService.requireUser();
            String title = (request == null || request.getTitle() == null || request.getTitle().isBlank())
                    ? "New chat"
                    : request.getTitle().trim();
            log.info("Creating session for user={} title={}", user.getId(), title);
            ChatSession session = ChatSession.builder()
                    .user(user)
                    .title(title)
                    .createdAt(Instant.now())
                    .build();
            ChatSession saved = sessionRepository.save(session);
            log.info("Session created id={}", saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new SessionCreateResponse(saved.getId(), saved.getCreatedAt()));
        } catch (Exception e) {
            log.error("Create session failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create session");
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listWithCount() {
        try {
            User user = currentUserService.requireUser();
            log.info("Listing sessions with message count for user={}", user.getId());
            List<ChatSession> sessions = sessionRepository.findByUserOrderByCreatedAtDesc(user);
            List<SessionSummaryResponse> summaries = sessions.stream()
                    .map(s -> new SessionSummaryResponse(
                            s.getId(),
                            s.getTitle(),
                            s.getCreatedAt(),
                            messageRepository.countBySession(s)
                    ))
                    .collect(Collectors.toList());
            log.info("Returning {} session summaries for user={}", summaries.size(), user.getId());
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            log.error("List sessions with count failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to list sessions");
        }
    }

    @GetMapping("/{sessionId}/history")
    public ResponseEntity<?> getHistory(@PathVariable Long sessionId) {
        try {
            User user = currentUserService.requireUser();
            log.info("Fetching full history for session={} user={}", sessionId, user.getId());
            Optional<ChatSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Session not found");
            }
            if (!sessionOpt.get().getUser().getId().equals(user.getId())) {
                log.warn("Forbidden access to session={} by user={}", sessionId, user.getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
            }
            List<ChatMessage> messages = messageRepository.findBySessionOrderByCreatedAtAsc(sessionOpt.get());
            log.info("Returning {} messages for session={}", messages.size(), sessionId);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Get history failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to load history");
        }
    }

    /**
     * Messages and the session are deleted in ONE transaction, so a failure part-way can't
     * leave a session with its messages already gone. The exception is caught to return a
     * clean 500, and a caught exception doesn't trigger rollback on its own — so the catch
     * marks the transaction rollback-only explicitly.
     */
    @Transactional
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable Long sessionId) {
        try {
            User user = currentUserService.requireUser();
            Optional<ChatSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty() || !sessionOpt.get().getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Session not found");
            }
            ChatSession session = sessionOpt.get();
            messageRepository.deleteBySession(session);   // delete children first
            sessionRepository.delete(session);             // then the session itself
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Delete session failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete session");
        }
    }

    /**
     * Distinguishes "client sent an empty message" from "client sent the wrong field name"
     * by echoing the raw JSON keys we actually received. Intended as a development-time aid
     * for the {@code messgae}/{@code message} typo class of bug — not meant to leak request
     * internals in a hardened production error response.
     */
    private Map<String, Object> buildBadMessageError(Map<String, Object> rawBody) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", "Message cannot be empty");
        if (rawBody == null || rawBody.isEmpty()) {
            error.put("detail", "Request body was empty or missing");
        } else if (!rawBody.containsKey("message")) {
            log.warn("postUserMessage: no 'message' key in request body, received keys={}", rawBody.keySet());
            error.put("detail", "No 'message' field found in request body — check for a field-name typo");
            error.put("receivedKeys", rawBody.keySet());
        } else {
            error.put("detail", "'message' field was blank");
        }
        return error;
    }
}

