package com.CognitiveMemory.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionCreateResponse {
    private Long sessionId;
    private Instant createdAt;
}
