package com.CognitiveMemory.demo.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/llm")
public class AIController {

    @PostMapping("/request")
    public ResponseEntity<?> promtByUser(@RequestBody String promt) {
        try {
            // MVP1 moved to /sessions/{id}/message which persists + calls Python
            return ResponseEntity.ok(promt);
        } catch (Exception e) {
            log.error("Error: ", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/response")
    public ResponseEntity<?> responseByAI() {
        try {
            String response = "This is a response from AI.";
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error: ", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

