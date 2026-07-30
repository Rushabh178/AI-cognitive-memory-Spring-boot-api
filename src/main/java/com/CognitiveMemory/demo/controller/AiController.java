package com.CognitiveMemory.demo.controller;

import com.CognitiveMemory.demo.dto.AiChatBody;
import com.CognitiveMemory.demo.dto.AiChatResponse;
import com.CognitiveMemory.demo.entity.User;
import com.CognitiveMemory.demo.gateway.PythonAiGateway;
import com.CognitiveMemory.demo.sessions.Repository.ChatMessageRepository;
import com.CognitiveMemory.demo.sessions.Repository.ChatSessionRepository;
import com.CognitiveMemory.demo.sessions.entity.ChatMessage;
import com.CognitiveMemory.demo.sessions.entity.ChatSession;
import com.CognitiveMemory.demo.sessions.service.CurrentUserService;
import com.CognitiveMemory.demo.utils.MemoryRelevanceUtil;
import com.CognitiveMemory.demo.utils.MemoryWorthinessUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    private final CurrentUserService currentUserService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final PythonAiGateway pythonAiGateway;

    public AiController(
            CurrentUserService currentUserService,
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            PythonAiGateway pythonAiGateway
    ) {
        this.currentUserService = currentUserService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.pythonAiGateway = pythonAiGateway;
    }

    /**
     * @deprecated Superseded by {@link SessionController#postUserMessage}, which uses the
     * cleaner {@code pythonAiGateway.chat()} abstraction. Kept for now to avoid breaking
     * existing callers; do not add new functionality here.
     */
    @Deprecated
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody AiChatBody body) {
        log.warn("AiController.chat() is deprecated and superseded by SessionController.postUserMessage; " +
                "callers should migrate to POST /sessions/{sessionId}/message");
        try {
            User user = currentUserService.requireUser();
            String userId = String.valueOf(user.getId());
            log.info("Chat request: user={} session={}", userId, body.getSessionId());

            // Verify session belongs to this user
            Optional<ChatSession> sessionOpt = sessionRepository.findById(body.getSessionId());
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Session not found");
            }
            ChatSession session = sessionOpt.get();
            if (!session.getUser().getId().equals(user.getId())) {
                log.warn("Forbidden: user={} does not own session={}", userId, body.getSessionId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
            }

            // Step 1: Save user message
            ChatMessage userMsg = messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role("user")
                    .content(body.getMessage().trim())
                    .createdAt(Instant.now())
                    .build());
            log.info("Step 1 complete: saved user message id={}", userMsg.getId());

            // Step 2: Retrieve top 5 relevant memories BEFORE the current message is stored,
            // so only genuinely PAST memories are ever returned as context — skipped for
            // context-independent queries (e.g. generic coding questions) to avoid leaking
            // unrelated personal context
            List<String> memories;
            if (MemoryRelevanceUtil.isMemoryRelevant(userMsg.getContent())) {
                try {
                    memories = pythonAiGateway.retrieveMemory(userId, userMsg.getContent(), 5);
                } catch (RestClientException e) {
                    log.error("Step 2 failed — memory retrieval error: {}", e.getMessage());
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body("Memory retrieval failed — AI service may be down. Check Python service on port 8000.");
                }
                log.info("Step 2 complete: retrieved {} relevant memories", memories.size());
                // TODO MVP 2: POST /graph/context {userId, query: message, topN: 5} — append graph_context_text to context
            } else {
                log.info("Step 2 skipped: message deemed context-independent, using empty context");
                memories = List.of();
            }

            // Step 3: Build context string, excluding memories that are near-duplicates
            // of the current message (a memory can only leak in as a duplicate if it was
            // stored moments ago in a prior request — filtering it out here is a safety net)
            List<String> filteredMemories = MemoryWorthinessUtil.filterNearDuplicates(userMsg.getContent(), memories);
            String context = String.join("\n", filteredMemories);
            log.info("Step 3 complete: context built ({} chars, {} of {} memories kept)",
                    context.length(), filteredMemories.size(), memories.size());

            // Step 4: Call AI with message + context
            String answer;
            try {
                answer = pythonAiGateway.sendToAi(userId, userMsg.getContent(), context);
            } catch (RestClientException e) {
                log.error("Step 4 failed — AI call error: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("AI response generation failed — check Python service on port 8000 and LLM API key.");
            }
            log.info("Step 4 complete: AI response received ({} chars)", answer.length());

            // Step 5: Store the user message as memory now that the response has been
            // generated, so it becomes context for FUTURE conversations only — and only
            // if it is actually memory-worthy. A store failure must never fail the request.
            if (MemoryWorthinessUtil.isMemoryWorthy(userMsg.getContent())) {
                try {
                    pythonAiGateway.storeMemory(userId, userMsg.getContent(), "user");
                    log.info("Step 5 complete: user message stored as memory");
                } catch (Exception ex) {
                    log.warn("Step 5: failed to store user memory, continuing: {}", ex.getMessage());
                }
            } else {
                log.info("Message not memory-worthy — skipping storage");
            }

            // Step 6: Save AI response
            ChatMessage aiMsg = messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role("assistant")
                    .content(answer)
                    .createdAt(Instant.now())
                    .build());
            log.info("Step 6 complete: saved AI message id={}", aiMsg.getId());

            // Also store the AI response as memory if substantial — responses under 20
            // words are usually too short to be meaningful memories. Never fail the
            // response just because assistant memory storage failed.
            if (answer != null && answer.split("\\s+").length > 20) {
                try {
                    pythonAiGateway.storeMemory(userId, answer, "assistant");
                    log.info("AI response stored as memory");
                } catch (Exception ex) {
                    log.warn("Failed to store assistant memory: {}", ex.getMessage());
                }
            }

            // Step 7: Return response to client
            return ResponseEntity.ok(new AiChatResponse(aiMsg.getContent()));

        } catch (RestClientException e) {
            log.error("Chat failed — Python AI service unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("AI service is currently unavailable. Please ensure the Python service is running on port 8000.");
        } catch (Exception e) {
            log.error("Chat pipeline failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process chat request");
        }
    }
}
