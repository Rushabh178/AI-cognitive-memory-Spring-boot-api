package com.CognitiveMemory.demo.gateway;

import com.CognitiveMemory.demo.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Service
public class PythonAiGateway {

    private final RestClient aiRestClient;

    public PythonAiGateway(RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    /**
     * Expected Python endpoints (FastAPI):
     * - POST /chat             -> AiChatResponse
     * - POST /memory/store     -> 200 OK
     * - POST /memory/retrieve  -> MemoryRetrieveResponse
     */
    public AiChatResponse chat(AiChatRequest request) {
        try {
            return aiRestClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiChatResponse.class);
        } catch (RestClientException ex) {
            // MVP1: fail safely instead of crashing the API gateway
            return new AiChatResponse("AI service unavailable (check `ai.base-url`).");
        }
    }

    public boolean storeMemory(MemoryStoreRequest request) {
        try {
            aiRestClient.post()
                    .uri("/memory/store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            return false;
        }
    }

    public MemoryRetrieveResponse retrieveMemories(MemoryRetrieveRequest request) {
        try {
            MemoryRetrieveResponse resp = aiRestClient.post()
                    .uri("/memory/retrieve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MemoryRetrieveResponse.class);
            return resp == null ? new MemoryRetrieveResponse(java.util.List.of()) : resp;
        } catch (RestClientException ex) {
            return new MemoryRetrieveResponse(java.util.List.of());
        }
    }

    // --- Convenience methods used by the chat pipeline; exceptions propagate so callers can return 503 ---

    public void storeMemory(String userId, String content, String role) {
        log.info("Storing memory for user={} role={}", userId, role);
        aiRestClient.post()
                .uri("/memory/store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MemoryStoreRequest(userId, null, content, role))
                .retrieve()
                .toBodilessEntity();
        log.info("Memory stored for user={}", userId);
    }

    public List<String> retrieveMemory(String userId, String query, int topK) {
        log.info("Retrieving top {} memories for user={}", topK, userId);
        MemoryRetrieveResponse resp = aiRestClient.post()
                .uri("/memory/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MemoryRetrieveRequest(userId, query, topK))
                .retrieve()
                .body(MemoryRetrieveResponse.class);
        List<String> memories = (resp != null && resp.getMemories() != null)
                ? resp.getMemories() : List.of();
        log.info("Retrieved {} memories for user={}", memories.size(), userId);
        return memories;
    }

    public String sendToAi(String userId, String message, String context) {
        log.info("Sending message to AI for user={}", userId);
        AiChatResponse resp = aiRestClient.post()
                .uri("/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiSendRequest(userId, message, context))
                .retrieve()
                .body(AiChatResponse.class);
        String answer = (resp != null && resp.getAnswer() != null) ? resp.getAnswer() : "";
        log.info("AI response received for user={}, answerLength={}", userId, answer.length());
        return answer;
    }
}

