package com.CognitiveMemory.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryIntentResponse {
    private String action; // "created" | "updated"
    private String nodeId;
}
