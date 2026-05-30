package com.CognitiveMemory.demo.controller;

import com.CognitiveMemory.demo.dto.AiChatRequest;
import com.CognitiveMemory.demo.dto.AiChatResponse;
import com.CognitiveMemory.demo.dto.CreateSessionRequest;
import com.CognitiveMemory.demo.dto.MemoryStoreRequest;
import com.CognitiveMemory.demo.dto.PostMessageRequest;
import com.CognitiveMemory.demo.entity.User;
import com.CognitiveMemory.demo.gateway.PythonAiGateway;
import com.CognitiveMemory.demo.sessions.Repository.ChatMessageRepository;
import com.CognitiveMemory.demo.sessions.Repository.ChatSessionRepository;
import com.CognitiveMemory.demo.sessions.entity.ChatMessage;
import com.CognitiveMemory.demo.sessions.entity.ChatSession;
import com.CognitiveMemory.demo.sessions.service.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
            @RequestBody PostMessageRequest request
    ) {
        try {
            User user = currentUserService.requireUser();
            Optional<ChatSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty() || !sessionOpt.get().getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Session not found");
            }
            if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Message cannot be empty");
            }

            ChatSession session = sessionOpt.get();
            ChatMessage userMsg = messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role("user")
                    .content(request.getMessage().trim())
                    .createdAt(Instant.now())
                    .build());

            pythonAiGateway.storeMemory(new MemoryStoreRequest(
                    String.valueOf(user.getId()),
                    String.valueOf(session.getId()),
                    userMsg.getContent(),
                    "user"
            ));

            AiChatResponse ai = pythonAiGateway.chat(new AiChatRequest(
                    String.valueOf(user.getId()),
                    String.valueOf(session.getId()),
                    userMsg.getContent()
            ));

            String answer = (ai == null || ai.getAnswer() == null) ? "" : ai.getAnswer();
            ChatMessage assistantMsg = messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role("assistant")
                    .content(answer)
                    .createdAt(Instant.now())
                    .build());

            pythonAiGateway.storeMemory(new MemoryStoreRequest(
                    String.valueOf(user.getId()),
                    String.valueOf(session.getId()),
                    assistantMsg.getContent(),
                    "assistant"
            ));

            return ResponseEntity.ok(assistantMsg);
        } catch (Exception e) {
            log.error("Post message failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send message");
        }
    }
}

