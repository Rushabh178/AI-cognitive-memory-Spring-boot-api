package com.CognitiveMemory.demo.controller;

import com.CognitiveMemory.demo.Repository.UserRepository;
import com.CognitiveMemory.demo.dto.JwtResponse;
import com.CognitiveMemory.demo.dto.LoginRequest;
import com.CognitiveMemory.demo.dto.RefreshTokenRequest;
import com.CognitiveMemory.demo.entity.RefreshToken;
import com.CognitiveMemory.demo.entity.User;
import com.CognitiveMemory.demo.service.RefreshTokenService;
import com.CognitiveMemory.demo.service.UserDetailsServiceImp;
import com.CognitiveMemory.demo.service.UserService;
import com.CognitiveMemory.demo.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/public")
@Slf4j
public class PublicController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsServiceImp userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RestClient aiRestClient;

    @GetMapping("/health-check")
    public String healthCheck() {
        return "OK";
    }

    /**
     * Describes the current API contract so a frontend developer can verify
     * field names without reading Java source. Kept in sync with the real,
     * non-deprecated endpoints only — POST /ai/chat is intentionally omitted
     * here since it is @Deprecated in favor of POST /sessions/{sessionId}/message.
     */
    @GetMapping("/contract-test")
    public ResponseEntity<Map<String, Object>> contractTest() {
        Map<String, Object> login = new LinkedHashMap<>();
        login.put("method", "POST");
        login.put("path", "/public/login");
        login.put("requestFields", List.of("userNameOrEmail", "password"));
        login.put("responseFields", List.of("accessToken", "refreshToken", "username"));

        Map<String, Object> signup = new LinkedHashMap<>();
        signup.put("method", "POST");
        signup.put("path", "/public/signup");
        signup.put("requestFields", List.of("userName", "email", "password", "firstName", "lastName", "mobileNo"));
        signup.put("requiredFields", List.of("userName", "email", "password"));
        signup.put("optionalFields", List.of("firstName", "lastName", "mobileNo"));

        Map<String, Object> refresh = new LinkedHashMap<>();
        refresh.put("method", "POST");
        refresh.put("path", "/public/refresh");
        refresh.put("requestFields", List.of("refreshToken"));
        refresh.put("responseFields", List.of("accessToken", "refreshToken", "username"));

        Map<String, Object> createSession = new LinkedHashMap<>();
        createSession.put("method", "POST");
        createSession.put("path", "/sessions/create");
        createSession.put("requestFields", List.of("title"));
        createSession.put("requiredFields", List.of());
        createSession.put("optionalFields", List.of("title"));
        createSession.put("defaultValues", Map.of("title", "New chat"));
        createSession.put("responseFields", List.of("sessionId", "createdAt"));

        Map<String, Object> listSessions = new LinkedHashMap<>();
        listSessions.put("method", "GET");
        listSessions.put("path", "/sessions/list");
        listSessions.put("responseFields", List.of("sessionId", "title", "createdAt", "messageCount"));

        Map<String, Object> sessionHistory = new LinkedHashMap<>();
        sessionHistory.put("method", "GET");
        sessionHistory.put("path", "/sessions/{sessionId}/history");
        sessionHistory.put("responseFields", List.of("id", "role", "content", "createdAt"));

        Map<String, Object> sendMessage = new LinkedHashMap<>();
        sendMessage.put("method", "POST");
        sendMessage.put("path", "/sessions/{sessionId}/message");
        sendMessage.put("requestFields", List.of("message"));
        sendMessage.put("responseFields", List.of("id", "role", "content", "createdAt"));
        sendMessage.put("note", "POST /ai/chat also exists but is @Deprecated — use this endpoint instead.");

        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("login", login);
        endpoints.put("signup", signup);
        endpoints.put("refresh", refresh);
        endpoints.put("createSession", createSession);
        endpoints.put("listSessions", listSessions);
        endpoints.put("sessionHistory", sessionHistory);
        endpoints.put("sendMessage", sendMessage);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("endpoints", endpoints);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping-ai-service")
    public ResponseEntity<Map<String, String>> pingAiService() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> health = aiRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);

            Map<String, String> response = new LinkedHashMap<>();
            response.put("springBoot", "ok");
            response.put("pythonService", "ok");
            response.put("chromadb",   health != null ? String.valueOf(health.get("chromadb"))   : "unknown");
            response.put("embeddings", health != null ? String.valueOf(health.get("embeddings")) : "unknown");
            response.put("graph",      health != null ? String.valueOf(health.get("graph"))      : "unknown");
            response.put("llm",        health != null ? String.valueOf(health.get("llm"))        : "unknown");
            return ResponseEntity.ok(response);

        } catch (RestClientException e) {
            Map<String, String> response = new LinkedHashMap<>();
            response.put("springBoot", "ok");
            response.put("pythonService", "unreachable");
            response.put("error", "Connection refused — is Python service running on port 8000?");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody User user) {

        User existingUser =
                userRepository.findByUserName(user.getUserName());

        if (existingUser != null) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Username already exists");
        }

        userService.saveNewEntry(user);

        return ResponseEntity.ok(
                "User registered successfully"
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest loginRequest
    ) {

        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUserNameOrEmail(),
                            loginRequest.getPassword()
                    )
            );

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(
                            loginRequest.getUserNameOrEmail()
                    );

            String accessToken =
                    jwtUtil.generateToken(
                            userDetails.getUsername()
                    );

            User user =
                    userService.findByUsernameOrEmail(
                            userDetails.getUsername()
                    );

            RefreshToken refreshToken =
                    refreshTokenService.createRefreshToken(
                            user.getId()
                    );

            JwtResponse response = new JwtResponse(
                    accessToken,
                    refreshToken.getToken(),
                    userDetails.getUsername()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            log.error("Error during login", e);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password");
        }
    }

    /**
     * Exchange a valid refresh token for a new access token (and rotated refresh token).
     * Lets clients stay logged in without re-entering credentials while the refresh token is valid.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            if (request == null
                    || request.getRefreshToken() == null
                    || request.getRefreshToken().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Refresh token is required");
            }

            RefreshToken stored = refreshTokenService
                    .findByToken(request.getRefreshToken().trim())
                    .orElse(null);

            if (stored == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid refresh token");
            }

            stored = refreshTokenService.verifyExpiration(stored);

            User user = stored.getUser();
            String username = user.getUserName();

            String accessToken = jwtUtil.generateToken(username);

            RefreshToken rotated = refreshTokenService.createRefreshToken(user.getId());

            JwtResponse response = new JwtResponse(
                    accessToken,
                    rotated.getToken(),
                    username
            );

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.warn("Refresh token rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error during token refresh", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to refresh token");
        }
    }
}

