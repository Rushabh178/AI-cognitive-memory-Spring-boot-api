package com.CognitiveMemory.demo.controller;

import com.CognitiveMemory.demo.dto.MemoryRetrieveRequest;
import com.CognitiveMemory.demo.dto.MemoryRetrieveResponse;
import com.CognitiveMemory.demo.dto.MemoryStoreBody;
import com.CognitiveMemory.demo.gateway.PythonAiGateway;
import com.CognitiveMemory.demo.sessions.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

@Slf4j
@RestController
@RequestMapping("/memory")
public class MemoryController {

    private final PythonAiGateway pythonAiGateway;
    private final CurrentUserService currentUserService;

    public MemoryController(
            PythonAiGateway pythonAiGateway,
            CurrentUserService currentUserService
    ) {
        this.pythonAiGateway = pythonAiGateway;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/store")
    public ResponseEntity<?> store(@Valid @RequestBody MemoryStoreBody body) {
        try {
            var user = currentUserService.requireUser();
            log.info("Storing memory for user={} role={}", user.getId(), body.getRole());
            pythonAiGateway.storeMemory(String.valueOf(user.getId()), body.getContent(), body.getRole());
            log.info("Memory store succeeded for user={}", user.getId());
            return ResponseEntity.ok().build();
        } catch (RestClientException e) {
            log.error("Memory store failed — Python service unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Memory service is currently unavailable");
        } catch (Exception e) {
            log.error("Memory store failed", e);
            return ResponseEntity.internalServerError().body("Failed to store memory");
        }
    }

    @PostMapping("/retrieve")
    public ResponseEntity<?> retrieve(
            @RequestParam(defaultValue = "5") int topK,
            @RequestBody String query
    ) {
        try {
            var user = currentUserService.requireUser();
            MemoryRetrieveResponse resp = pythonAiGateway.retrieveMemories(
                    new MemoryRetrieveRequest(String.valueOf(user.getId()), query, Math.max(1, topK))
            );
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Memory retrieve failed", e);
            return ResponseEntity.internalServerError().body("Failed to retrieve memories");
        }
    }
}

