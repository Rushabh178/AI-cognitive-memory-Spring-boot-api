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

    /**
     * Routes memory writes through the intent-aware Python endpoint, which distinguishes
     * new facts from duplicates/updates (e.g. task completion) instead of blindly appending —
     * replaces the old storeMemory + processGraph combo that caused duplicate-memory bugs.
     */
    public MemoryIntentResponse processMemoryIntent(String userId, String content) {
        log.info("Processing memory intent for user={}", userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("text", content);

        try {
            MemoryIntentResponse resp = aiRestClient.post()
                    .uri("/memory/process-intent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MemoryIntentResponse.class);
            return resp == null ? new MemoryIntentResponse("created", null) : resp;
        } catch (HttpClientErrorException ex) {
            log.error("DEBUG Python /memory/process-intent returned HTTP {} — Python error body: {}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw ex;
        }
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

    /**
     * @deprecated Only kept so the deprecated
     * {@link com.CognitiveMemory.demo.controller.AiController#chat} still compiles —
     * that endpoint predates session history and shouldn't gain new behavior. New code
     * should call the 4-arg overload below with a real sessionHistory list.
     */
    @Deprecated
    public String sendToAi(String userId, String message, String context) {
        return sendToAi(userId, message, context, List.of());
    }

    /**
     * sessionHistory carries the CURRENT session's prior turns (oldest first, each a
     * {"role": "user"|"assistant", "content": "..."} map) so the Python side can place them
     * as real prior messages ahead of the current one — that's what gives them priority
     * over the long-term memory in `context`, not just wording. Pass an empty list for the
     * first message of a session.
     */
    public String sendToAi(String userId, String message, String context, List<Map<String, String>> sessionHistory) {
        log.info("Sending message to AI for user={} historyTurns={}", userId, sessionHistory.size());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("message", message);
        body.put("context", context);
        body.put("sessionHistory", sessionHistory);

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

    /**
     * Fetches graph_context_text (relationship-aware context from the Postgres graph layer)
     * for a query. Returns "" on any failure or when the graph has nothing relevant —
     * never throws, since graph context is a supplementary layer and its absence shouldn't
     * fail the chat request.
     */
    public String getGraphContext(String userId, String query, int topN) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("query", query);
        body.put("topN", topN);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = aiRestClient.post()
                    .uri("/graph/context")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String text = (resp != null && resp.get("graph_context_text") instanceof String s) ? s : "";
            log.info("Graph context retrieved for user={} chars={}", userId, text.length());
            return text;
        } catch (Exception ex) {
            log.warn("Graph context unavailable, continuing without it: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * Fetches the latest rolling summary (Task 1's background job) for a user. Returns "" if
     * none has been generated yet (a normal, expected state — not an error) or on any
     * failure, since the summary is a supplementary context layer, not required for chat to work.
     */
    public String getLatestSummary(String userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = aiRestClient.get()
                    .uri("/graph/summary/{userId}", userId)
                    .retrieve()
                    .body(Map.class);

            if (resp != null && Boolean.TRUE.equals(resp.get("hasSummary")) && resp.get("summary") instanceof String s) {
                log.info("Latest summary retrieved for user={} chars={}", userId, s.length());
                return s;
            }
            return "";
        } catch (Exception ex) {
            log.warn("Summary unavailable, continuing without it: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * Fetches the user's revision history ("previously Rohit Sharma, replaced by Virat Kohli")
     * for a history-flavoured message — see HistoryQuestionUtil. Calls the Python service's
     * bounded GET /graph/history (at most {@code limit} changed facts, scoped by Python to the
     * query's domain), never the unbounded /graph/timeline. The query is sent so Python can
     * work out the domain; Spring has no way to classify it.
     * <p>
     * Returns "" when there's no history or on any failure — history is a supplementary
     * context layer, same as getGraphContext/getLatestSummary, and must never fail chat.
     */
    public String getGraphHistory(String userId, String query) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = aiRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/graph/history/{userId}")
                            .queryParam("query", query)
                            .queryParam("limit", 5)
                            .build(userId))
                    .retrieve()
                    .body(Map.class);

            String text = (resp != null && resp.get("history_context_text") instanceof String s) ? s : "";
            log.info("Graph history retrieved for user={} chars={} domain={}",
                    userId, text.length(), resp != null ? resp.get("domain") : null);
            return text;
        } catch (Exception ex) {
            log.warn("Graph history unavailable, continuing without it: {}", ex.getMessage());
            return "";
        }
    }
}
