package com.CognitiveMemory.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryStoreRequest {
    private String userId;
    private String sessionId;
    private String text;
    private String role; // "user" | "assistant"
}

