package com.shopmind.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.service.ai.AiService;
import com.shopmind.service.ai.GeminiAiService;
import com.shopmind.service.ai.GroqAiService;
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
 *   - "mock"   (default) → MockAiService (deterministic, no API key required)
 *   - "groq"             → GroqAiService  (Groq REST API — OpenAI-compatible, fast free tier)
 *   - "gemini"           → GeminiAiService (Google Gemini REST API)
 *
 * MockAiService is always registered and used as numeric-scoring fallback inside all LLM services.
 */
@Configuration
public class AiServiceConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "shopmind.ai.provider", havingValue = "groq")
    public AiService groqAiService(
        @Value("${shopmind.ai.groq-api-key:}") String apiKey,
        @Value("${shopmind.ai.groq-model:llama-3.3-70b-versatile}") String model,
        @Value("${shopmind.ai.max-tokens:2048}") long maxTokens,
        ObjectMapper objectMapper,
        MockAiService fallback
    ) {
        return new GroqAiService(apiKey, model, maxTokens, objectMapper, fallback);
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
