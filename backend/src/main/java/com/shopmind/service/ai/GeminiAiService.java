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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI service backed by the Google Gemini REST API.
 * Uses responseMimeType=application/json for strict JSON output.
 * Hybrid: MockAiService for numeric scores, Gemini for reasoning enrichment.
 * All calls fall back silently to MockAiService on any error.
 */
@Slf4j
public class GeminiAiService implements AiService {

    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    /**
     * Single combined prompt: extracts intent AND decides next question in one API call.
     * The question decision is cached in PENDING_DECISION so decideNextQuestion() can read it
     * without making a second network call.
     */
    private static final String COMBINED_SYSTEM_PROMPT = """
        You are a footwear shopping advisor. Given a conversation, do two things in one response:
        1. Extract the shopper's buying intent.
        2. Decide what to ask next (or whether you have enough to recommend).

        Respond with a single JSON object:
        {
          "primaryUseCase": "string or null",
          "gender": "male | female | unisex | null",
          "walkingDuration": "string or null",
          "budget": "number or null",
          "comfortPriority": "number 0-1 or null",
          "stylePriority": "number 0-1 or null",
          "durabilityPriority": "number 0-1 or null",
          "terrainType": "string or null",
          "preferredFit": "string or null",
          "needsVersatility": "boolean or null",
          "confidenceScore": "required number 0-1",
          "missingAttributes": "required string array",
          "contradictions": "string array",
          "readyToRecommend": "required boolean",
          "nextQuestion": "string or null",
          "questionReasoning": "string"
        }

        Rules:
        - Key attributes (7 total): primaryUseCase, gender, walkingDuration, budget, comfortPriority, stylePriority, preferredFit.
        - confidenceScore = (7 - missingAttributes.length) / 7.0, rounded to 2 decimals.
        - readyToRecommend = true when confidenceScore >= 0.65 OR missingAttributes is empty.
        - Ask about gender early — after primaryUseCase. Accept "men's", "women's", "for my girlfriend", "I'm a guy", etc.
          Infer from context: "shopping for my wife" → female, "for myself" + male pronouns → male.
        - nextQuestion must feel natural and conversational — reference what the user already told you.
        - Ask about one attribute at a time. Priority: primaryUseCase > walkingDuration > budget > comfortPriority.
        - NEVER repeat the same question verbatim. If the user's answer was too vague to extract a value,
          you MAY ask one follow-up clarification — but you MUST acknowledge what they said and ask a
          targeted follow-up phrased differently.
          Good: user says "sometimes" to hours → "Got it — on a typical day, closer to 2-3 hours or more like 5-6+?"
          Bad: user says "sometimes" → "How many hours are you typically on your feet each day?" (verbatim repeat)
        - Accept natural language directional answers without follow-up:
          "a few hours" = 2-3h, "most of the day" = 6-8h, "around 10k" = ₹10000,
          "I care about looks" = high stylePriority, "comfort matters most" = high comfortPriority.
        - If the user declined to give a budget or said it doesn't matter, drop "budget" from missingAttributes.
        - Prices are in INR (Indian Rupees). "10k" means ₹10,000.
        """;

    private static final String REASONING_SYSTEM_PROMPT = """
        You are a footwear advisor generating purchase reasoning for product recommendations.
        Given a shopper's intent and a list of scored shoe matches, write concise, honest reasoning.

        Respond with a JSON array:
        [
          {
            "productId": string,
            "reasoning": string,
            "tradeoffs": string,
            "notSuitableFor": string
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

    public GeminiAiService(String apiKey, String model, long maxTokens,
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
        pendingDecision.remove(); // clear any stale cache from a previous turn
        try {
            String userPrompt = "Conversation:\n" + formatHistory(conversationHistory)
                + "\n\nExtract intent and decide what to ask next.";
            String json = callGemini(COMBINED_SYSTEM_PROMPT, userPrompt, 768);
            JsonNode node = objectMapper.readTree(stripFences(json));

            // Parse intent
            ExtractedIntent intent = objectMapper.treeToValue(node, ExtractedIntent.class);
            if (intent.getMissingAttributes() == null) intent.setMissingAttributes(List.of());
            if (intent.getContradictions()     == null) intent.setContradictions(List.of());
            if (intent.getConfidenceScore()    == null) {
                double conf = 1.0 - (intent.getMissingAttributes().size() * 0.15);
                intent.setConfidenceScore(Math.max(0.1, Math.min(1.0, conf)));
            }
            if (intent.getTerrainType() == null) intent.setTerrainType("urban");

            // Cache the question decision so decideNextQuestion() avoids a second API call
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
            log.warn("Gemini intent+question call failed: {} — falling back to mock", e.getMessage());
            return fallback.extractIntent(conversationHistory);
        }
    }

    @Override
    public QuestionDecision decideNextQuestion(ExtractedIntent intent, int questionCount) {
        // Return the decision already parsed alongside the intent — no extra API call
        QuestionDecision cached = pendingDecision.get();
        if (cached != null) {
            pendingDecision.remove();
            // Apply hard cap: if questionCount >= 8 force readyToRecommend regardless of Gemini
            if (questionCount >= 8 && !cached.isReadyToRecommend()) {
                return QuestionDecision.builder()
                    .readyToRecommend(true)
                    .confidence(intent.getConfidenceScore())
                    .missingAttributes(List.of())
                    .build();
            }
            return cached;
        }
        // Cache miss (e.g. extractIntent fell back to mock) — use mock decision
        return fallback.decideNextQuestion(intent, questionCount);
    }

    @Override
    public List<ProductMatch> rankProducts(List<ProductData> products, ExtractedIntent intent) {
        List<ProductMatch> scored = fallback.rankProducts(products, intent);
        if (!canCall() || scored.isEmpty()) return scored;
        try {
            String json = callGemini(REASONING_SYSTEM_PROMPT, buildReasoningPrompt(scored, intent), 1024);
            JsonNode reasoningArray = objectMapper.readTree(stripFences(json));
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
            log.warn("Gemini reasoning failed: {} — using mock reasoning", e.getMessage());
            return scored;
        }
    }

    // ─── Gemini REST ──────────────────────────────────────────────────────────

    private boolean canCall() { return apiKey != null && !apiKey.isBlank(); }

    private WebClient getClient() {
        if (cachedClient == null) {
            synchronized (this) {
                if (cachedClient == null) {
                    cachedClient = WebClient.builder()
                        .baseUrl(GEMINI_BASE_URL)
                        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofSeconds(30))))
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                        .build();
                }
            }
        }
        return cachedClient;
    }

    private static final int MAX_RETRIES = 3;

    private String callGemini(String systemPrompt, String userPrompt, long tokenBudget) {
        Map<String, Object> body = Map.of(
            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "maxOutputTokens", Math.min(tokenBudget, maxTokens),
                "temperature", 0.4)
        );

        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                try {
                    long backoffMs = 1000L * (1L << attempt); // 2s, 4s
                    log.debug("Gemini retry {} after {}ms backoff", attempt, backoffMs);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during Gemini retry backoff", ie);
                }
            }
            try {
                JsonNode response = getClient().post()
                    .uri("/models/" + model + ":generateContent?key=" + apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(30));

                if (response == null) throw new IllegalStateException("Empty Gemini response");
                if (response.has("error")) throw new IllegalStateException("Gemini error: " + response.get("error"));

                JsonNode candidates = response.path("candidates");
                if (!candidates.isArray() || candidates.isEmpty())
                    throw new IllegalStateException("No candidates in Gemini response");

                StringBuilder text = new StringBuilder();
                for (JsonNode part : candidates.get(0).path("content").path("parts"))
                    text.append(part.path("text").asText(""));

                String result = text.toString();
                if (result.isBlank()) throw new IllegalStateException("Empty Gemini content");
                return result;
            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("429") || msg.contains("Too Many Requests") || msg.contains("RESOURCE_EXHAUSTED")) {
                    log.warn("Gemini 429 rate limit on attempt {}/{} — retrying with backoff", attempt + 1, MAX_RETRIES);
                } else {
                    throw e; // non-retriable error — fail fast
                }
            }
        }
        throw new IllegalStateException("Gemini rate-limited after " + MAX_RETRIES + " attempts", lastException);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String formatHistory(List<Map<String, String>> h) {
        return h.stream()
            .map(m -> "[" + capitalize(m.getOrDefault("role","user")) + "]: " + m.getOrDefault("content",""))
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

    private String stripFences(String raw) {
        if (raw == null) return "{}";
        String t = raw.trim();
        Matcher m = JSON_FENCE.matcher(t);
        if (m.find()) return m.group(1).trim();
        return t;
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
