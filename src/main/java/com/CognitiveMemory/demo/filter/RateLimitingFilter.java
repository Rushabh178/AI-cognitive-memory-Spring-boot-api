package com.CognitiveMemory.demo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${request.max-requests:60}")
    private int maxRequests;

    @Value("${request.time-window-ms:60000}")
    private long timeWindowMs;

    private final Map<String, RequestInfo> requestMap =
            new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();

        long currentTime = Instant.now().toEpochMilli();

        RequestInfo requestInfo = requestMap.getOrDefault(
                clientIp,
                new RequestInfo(0, currentTime)
        );

        // RESET WINDOW
        if (currentTime - requestInfo.startTime > timeWindowMs) {

            requestInfo.count = 0;
            requestInfo.startTime = currentTime;
        }

        requestInfo.count++;

        requestMap.put(clientIp, requestInfo);

        if (requestInfo.count > maxRequests) {

            response.setStatus(
                    HttpStatus.TOO_MANY_REQUESTS.value()
            );

            response.setContentType("application/json");

            response.getWriter().write("""
                    {
                      "error": "Too many requests",
                      "message": "Please try again later"
                    }
                    """);

            return;
        }

        filterChain.doFilter(request, response);
    }

    private static class RequestInfo {

        int count;

        long startTime;

        public RequestInfo(int count, long startTime) {
            this.count = count;
            this.startTime = startTime;
        }
    }
}
