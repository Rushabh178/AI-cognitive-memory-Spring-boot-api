package com.CognitiveMemory.demo.gateway;

import com.CognitiveMemory.demo.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PythonAiGateway {

    private final RestClient aiRestClient;

    @Value("${python.ai.service.bearer-token:MISSING}")
    private String configuredBearerToken;

    public PythonAiGateway(RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    // --- Legacy overloads (not used by the chat pipeline) ---

    public AiChatResponse chat(AiChatRequest request) {
        try {
            return aiRestClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiChatResponse.class);
        } catch (RestClientException ex) {
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

    // --- Pipeline methods used by AiController ---
    // Bodies are sent as Map<String, Object> so Jackson serialises standard JDK
    // types — no application-class reflection needed, bypassing any class-loader
    // mismatch that occurs with DevTools when using the static RestClient.builder().
    // Responses are parsed into Map.class for the same reason.

    public void storeMemory(String userId, String content, String role) {
        log.info("Storing memory for user={} role={}", userId, role);
        log.info("DEBUG storeMemory → userId='{}' content='{}' role='{}'", userId, content, role);
        log.info("DEBUG bearer token configured: length={}", configuredBearerToken.length());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("sessionId", null);
        body.put("text", content);
        body.put("role", role);
        log.info("DEBUG body map: {}", body);

        try {
            aiRestClient.post()
                    .uri("/memory/store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Memory stored for user={}", userId);
        } catch (HttpClientErrorException ex) {
            log.error("DEBUG Python /memory/store returned HTTP {} — Python error body: {}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    public List<String> retrieveMemory(String userId, String query, int topK) {
        log.info("Retrieving top {} memories for user={}", topK, userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("query", query);
        body.put("topK", topK);

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = aiRestClient.post()
                .uri("/memory/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        List<String> memories;
        if (resp != null && resp.get("memories") instanceof List<?> list) {
            memories = list.stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        } else {
            memories = List.of();
        }
        log.info("Retrieved {} memories for user={}", memories.size(), userId);
        return memories;
    }

    public void processGraph(String userId, String text, String memoryId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("text", text);
        body.put("memoryId", memoryId);
        try {
            aiRestClient.post()
                    .uri("/graph/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Graph processed for memoryId={}", memoryId);
        } catch (Exception ex) {
            log.warn("Graph processing failed, continuing: {}", ex.getMessage());
        }
    }

    public String sendToAi(String userId, String message, String context) {
        log.info("Sending message to AI for user={}", userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("message", message);
        body.put("context", context);

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = aiRestClient.post()
                .uri("/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        String answer = (resp != null && resp.get("answer") instanceof String s) ? s : "";
        log.info("AI response received for user={}, answerLength={}", userId, answer.length());
        return answer;
    }
}
