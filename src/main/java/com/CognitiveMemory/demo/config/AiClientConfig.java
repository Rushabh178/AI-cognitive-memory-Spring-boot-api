package com.CognitiveMemory.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class AiClientConfig {

    @Bean
    public RestClient aiRestClient(
            @Value("${python.ai.service.base-url}") String baseUrl,
            @Value("${python.ai.service.bearer-token}") String bearerToken
    ) {
        // Force HTTP/1.1 — Uvicorn does not support HTTP/2 upgrades
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + bearerToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
