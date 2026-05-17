package com.shopmind.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.service.ai.AiService;
import com.shopmind.service.ai.ClaudeAiService;
import com.shopmind.service.ai.GeminiAiService;
import com.shopmind.service.ai.MockAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects the active {@link AiService} bean based on {@code shopmind.ai.provider}.
 *
 * Provider values:
 *   - "mock"   (default) → MockAiService (deterministic, no API key)
 *   - "claude"           → ClaudeAiService (Anthropic SDK)
 *   - "gemini"           → GeminiAiService (Google Gemini REST API via WebClient)
 *
 * Bean wiring rules:
 *   - MockAiService is always a bean (registered via @Service) — used directly when provider=mock
 *     and injected as the fallback into Claude/Gemini services when a real LLM is active.
 *   - When provider=claude or provider=gemini, this config registers a @Primary real-AI bean
 *     that the conversation pipeline transparently routes through.
 *
 * Each real-AI bean handles its own exception-safe fallback to mock per-call, so a missing or
 * invalid API key never produces a 500 response — it just behaves like mock mode for that call.
 */
@Configuration
public class AiServiceConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "shopmind.ai.provider", havingValue = "claude")
    public AiService claudeAiService(
        @Value("${shopmind.ai.anthropic-api-key:}") String apiKey,
        @Value("${shopmind.ai.claude-model:claude-haiku-4-5-20251001}") String model,
        @Value("${shopmind.ai.max-tokens:2048}") long maxTokens,
        ObjectMapper objectMapper,
        MockAiService fallback
    ) {
        return new ClaudeAiService(apiKey, model, maxTokens, objectMapper, fallback);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "shopmind.ai.provider", havingValue = "gemini")
    public AiService geminiAiService(
        @Value("${shopmind.ai.gemini-api-key:}") String apiKey,
        @Value("${shopmind.ai.gemini-model:gemini-2.0-flash}") String model,
        @Value("${shopmind.ai.max-tokens:2048}") long maxTokens,
        ObjectMapper objectMapper,
        MockAiService fallback
    ) {
        return new GeminiAiService(apiKey, model, maxTokens, objectMapper, fallback);
    }
}
