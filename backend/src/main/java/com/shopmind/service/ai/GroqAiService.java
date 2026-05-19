package com.shopmind.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.dto.AiDtos.ExtractedIntent;
import com.shopmind.dto.AiDtos.ProductData;
import com.shopmind.dto.AiDtos.ProductMatch;
import com.shopmind.dto.AiDtos.QuestionDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI service backed by the Groq REST API (OpenAI-compatible).
 * Uses response_format=json_object for structured output.
 * Combines intent extraction + question decision into one call per message turn.
 * Falls back silently to MockAiService on any error or rate limit.
 */
@Slf4j
public class GroqAiService implements AiService {

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";

    private static final String COMBINED_SYSTEM_PROMPT = """
        You are a footwear shopping advisor. Given a conversation, do two things in one response:
        1. Extract the shopper's buying intent.
        2. Decide what to ask next (or whether you have enough to recommend).

        Respond with a single JSON object — no markdown fences, raw JSON only:
        {
          "primaryUseCase": "string or null",
          "walkingDuration": "string or null",
          "budget": 0,
          "comfortPriority": 0.0,
          "stylePriority": 0.0,
          "durabilityPriority": 0.0,
          "terrainType": "string or null",
          "preferredFit": "string or null",
          "needsVersatility": false,
          "confidenceScore": 0.0,
          "missingAttributes": [],
          "contradictions": [],
          "readyToRecommend": false,
          "nextQuestion": "string or null",
          "questionReasoning": "string"
        }

        Rules:
        - Key attributes: primaryUseCase, walkingDuration, budget, comfortPriority, stylePriority, preferredFit.
        - confidenceScore = (6 - missingAttributes.length) / 6.0, rounded to 2 decimals.
        - readyToRecommend = true when confidenceScore >= 0.65 OR missingAttributes is empty.
        - nextQuestion must feel natural and conversational — reference what the user already told you.
        - Ask about one attribute at a time. Priority: primaryUseCase > walkingDuration > budget > comfortPriority.
        - Never ask about an attribute already in the intent.
        - If the user declined to give a budget, drop "budget" from missingAttributes.
        - Prices are in INR (Indian Rupees).
        """;

    private static final String REASONING_SYSTEM_PROMPT = """
        You are a footwear advisor generating purchase reasoning for product recommendations.
        Given a shopper's intent and a list of scored shoe matches, write concise, honest reasoning.

        Respond with a JSON array — no markdown fences, raw JSON only:
        [
          {
            "productId": "string",
            "reasoning": "string",
            "tradeoffs": "string",
            "notSuitableFor": "string"
          }
        ]

        Rules:
        - Reference the shopper's actual stated needs.
        - Be specific and honest about tradeoffs.
        - Keep reasoning under 60 words per product.
        - Use pipe (|) as separator inside tradeoffs and notSuitableFor.
        - Prices are in INR.
        """;

    private final ObjectMapper objectMapper;
    private final MockAiService fallback;
    private final String apiKey;
    private final String model;
    private final long maxTokens;

    private volatile WebClient cachedClient;

    /** Caches the question decision parsed alongside intent in the same API call. */
    private final ThreadLocal<QuestionDecision> pendingDecision = new ThreadLocal<>();

    private static final int MAX_RETRIES = 3;

    public GroqAiService(String apiKey, String model, long maxTokens,
                         ObjectMapper objectMapper, MockAiService fallback) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public ExtractedIntent extractIntent(List<Map<String, String>> conversationHistory) {
        if (!canCall()) return fallback.extractIntent(conversationHistory);
        pendingDecision.remove();
        try {
            String userPrompt = "Conversation:\n" + formatHistory(conversationHistory)
                + "\n\nExtract intent and decide what to ask next.";
            String json = callGroq(COMBINED_SYSTEM_PROMPT, userPrompt, 768);
            JsonNode node = objectMapper.readTree(json);

            ExtractedIntent intent = objectMapper.treeToValue(node, ExtractedIntent.class);
            if (intent.getMissingAttributes() == null) intent.setMissingAttributes(List.of());
            if (intent.getContradictions()     == null) intent.setContradictions(List.of());
            if (intent.getConfidenceScore()    == null) {
                double conf = 1.0 - (intent.getMissingAttributes().size() * 0.15);
                intent.setConfidenceScore(Math.max(0.1, Math.min(1.0, conf)));
            }
            if (intent.getTerrainType() == null) intent.setTerrainType("urban");

            QuestionDecision decision = QuestionDecision.builder()
                .readyToRecommend(node.path("readyToRecommend").asBoolean(false))
                .nextQuestion(node.hasNonNull("nextQuestion") ? node.get("nextQuestion").asText() : null)
                .reasoning(node.path("questionReasoning").asText(""))
                .confidence(intent.getConfidenceScore())
                .missingAttributes(intent.getMissingAttributes())
                .build();
            pendingDecision.set(decision);

            return intent;
        } catch (Exception e) {
            log.warn("Groq intent+question call failed: {} — falling back to mock", e.getMessage());
            return fallback.extractIntent(conversationHistory);
        }
    }

    @Override
    public QuestionDecision decideNextQuestion(ExtractedIntent intent, int questionCount) {
        QuestionDecision cached = pendingDecision.get();
        if (cached != null) {
            pendingDecision.remove();
            if (questionCount >= 8 && !cached.isReadyToRecommend()) {
                return QuestionDecision.builder()
                    .readyToRecommend(true)
                    .confidence(intent.getConfidenceScore())
                    .missingAttributes(List.of())
                    .build();
            }
            return cached;
        }
        return fallback.decideNextQuestion(intent, questionCount);
    }

    @Override
    public List<ProductMatch> rankProducts(List<ProductData> products, ExtractedIntent intent) {
        List<ProductMatch> scored = fallback.rankProducts(products, intent);
        if (!canCall() || scored.isEmpty()) return scored;
        try {
            String json = callGroq(REASONING_SYSTEM_PROMPT, buildReasoningPrompt(scored, intent), 1024);
            JsonNode reasoningArray = objectMapper.readTree(json);
            if (!reasoningArray.isArray()) return scored;

            Map<String, JsonNode> map = new HashMap<>();
            for (JsonNode item : reasoningArray) {
                String id = item.path("productId").asText("");
                if (!id.isEmpty()) map.put(id, item);
            }
            return scored.stream().map(m -> {
                JsonNode r = map.get(m.getProductId());
                if (r == null) return m;
                return ProductMatch.builder()
                    .productId(m.getProductId()).productName(m.getProductName()).brand(m.getBrand())
                    .imageUrl(m.getImageUrl()).price(m.getPrice()).matchScore(m.getMatchScore())
                    .comfortScore(m.getComfortScore()).durabilityScore(m.getDurabilityScore())
                    .styleScore(m.getStyleScore())
                    .reasoning(textOrFallback(r, "reasoning", m.getReasoning()))
                    .tradeoffs(textOrFallback(r, "tradeoffs", m.getTradeoffs()))
                    .notSuitableFor(textOrFallback(r, "notSuitableFor", m.getNotSuitableFor()))
                    .regretFlags(m.getRegretFlags()).merchants(m.getMerchants()).build();
            }).toList();
        } catch (Exception e) {
            log.warn("Groq reasoning failed: {} — using mock reasoning", e.getMessage());
            return scored;
        }
    }

    // ─── Groq REST ────────────────────────────────────────────────────────────

    private boolean canCall() { return apiKey != null && !apiKey.isBlank(); }

    private WebClient getClient() {
        if (cachedClient == null) {
            synchronized (this) {
                if (cachedClient == null) {
                    cachedClient = WebClient.builder()
                        .baseUrl(GROQ_BASE_URL)
                        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofSeconds(30))))
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                        .build();
                }
            }
        }
        return cachedClient;
    }

    private String callGroq(String systemPrompt, String userPrompt, long tokenBudget) {
        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user",   "content", userPrompt)
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.4);
        body.put("max_tokens", (int) Math.min(tokenBudget, maxTokens));
        body.put("response_format", Map.of("type", "json_object"));

        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                try {
                    long backoffMs = 1000L * (1L << attempt); // 2s, 4s
                    log.debug("Groq retry {} after {}ms backoff", attempt, backoffMs);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during Groq retry backoff", ie);
                }
            }
            try {
                JsonNode response = getClient().post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(30));

                if (response == null) throw new IllegalStateException("Empty Groq response");
                if (response.has("error")) throw new IllegalStateException("Groq error: " + response.get("error"));

                String content = response.path("choices").path(0).path("message").path("content").asText("");
                if (content.isBlank()) throw new IllegalStateException("Empty Groq content");
                return content;
            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("429") || msg.contains("Too Many Requests") || msg.contains("rate_limit")) {
                    log.warn("Groq 429 rate limit on attempt {}/{} — retrying with backoff", attempt + 1, MAX_RETRIES);
                } else {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Groq rate-limited after " + MAX_RETRIES + " attempts", lastException);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String formatHistory(List<Map<String, String>> h) {
        return h.stream()
            .map(m -> "[" + capitalize(m.getOrDefault("role", "user")) + "]: " + m.getOrDefault("content", ""))
            .collect(Collectors.joining("\n"));
    }

    private String buildReasoningPrompt(List<ProductMatch> matches, ExtractedIntent i) {
        try {
            List<Map<String, Object>> compact = matches.stream().map(m -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("productId", m.getProductId()); e.put("productName", m.getProductName());
                e.put("brand", m.getBrand()); e.put("priceINR", m.getPrice());
                e.put("matchScore", m.getMatchScore()); e.put("comfortScore", m.getComfortScore());
                e.put("durabilityScore", m.getDurabilityScore()); e.put("styleScore", m.getStyleScore());
                return e;
            }).toList();
            return "Shopper's needs:\n- Use case: " + orNull(i.getPrimaryUseCase())
                + "\n- Hours on feet: " + orNull(i.getWalkingDuration())
                + "\n- Budget: " + (i.getBudget() != null ? "₹" + i.getBudget().intValue() : "unspecified")
                + "\n- Comfort priority: " + orNull(i.getComfortPriority())
                + "\n- Style priority: " + orNull(i.getStylePriority())
                + "\n- Terrain: " + orNull(i.getTerrainType())
                + "\n\nProducts:\n" + objectMapper.writeValueAsString(compact)
                + "\n\nWrite honest reasoning for each product.";
        } catch (Exception e) { throw new IllegalStateException("Failed to build reasoning prompt", e); }
    }

    private String textOrFallback(JsonNode n, String key, String fb) {
        if (n == null || !n.hasNonNull(key)) return fb;
        String v = n.get(key).asText(""); return v.isEmpty() ? fb : v;
    }

    private String capitalize(String s) {
        return (s == null || s.isEmpty()) ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String orNull(Object v) { return v == null ? "null" : String.valueOf(v); }
}
