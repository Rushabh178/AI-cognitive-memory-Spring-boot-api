package com.CognitiveMemory.demo.controller;

import com.CognitiveMemory.demo.dto.MemoryRetrieveRequest;
import com.CognitiveMemory.demo.dto.MemoryRetrieveResponse;
import com.CognitiveMemory.demo.gateway.PythonAiGateway;
import com.CognitiveMemory.demo.sessions.service.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

