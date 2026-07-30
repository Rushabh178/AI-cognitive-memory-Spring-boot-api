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
    // MVP flag so Python can skip its own memory retrieval for context-independent
    // queries (see MemoryRelevanceUtil). Defaults to true via no-args construction.
    private boolean useMemory = true;
}

