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

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody AiChatBody body) {
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

            // Step 2: Store user message in memory
            pythonAiGateway.storeMemory(userId, userMsg.getContent(), "user");
            log.info("Step 2 complete: stored user message in memory");

            // Step 3: Retrieve top 5 relevant memories
            List<String> memories = pythonAiGateway.retrieveMemory(userId, userMsg.getContent(), 5);
            log.info("Step 3 complete: retrieved {} relevant memories", memories.size());

            // Step 4: Build context string from retrieved memories
            String context = memories.isEmpty() ? "" : String.join("\n", memories);
            log.info("Step 4 complete: context built ({} chars)", context.length());

            // Step 5: Call AI with message + context
            String answer = pythonAiGateway.sendToAi(userId, userMsg.getContent(), context);
            log.info("Step 5 complete: AI response received ({} chars)", answer.length());

            // Step 6: Save AI response
            ChatMessage aiMsg = messageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role("assistant")
                    .content(answer)
                    .createdAt(Instant.now())
                    .build());
            log.info("Step 6 complete: saved AI message id={}", aiMsg.getId());

            // Step 7: Return response to client
            return ResponseEntity.ok(new AiChatResponse(aiMsg.getContent()));

        } catch (RestClientException e) {
            log.error("Chat failed — Python AI service unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("AI service is currently unavailable, please try again later");
        } catch (Exception e) {
            log.error("Chat pipeline failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process chat request");
        }
    }
}
