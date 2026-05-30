package com.CognitiveMemory.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    private String userId;
    private String sessionId;
    private String message;
}

