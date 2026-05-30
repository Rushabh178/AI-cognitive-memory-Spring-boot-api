package com.CognitiveMemory.demo.gateway;

import com.CognitiveMemory.demo.dto.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
}

