package com.shopmind.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.dto.AiDtos.ExtractedIntent;
import com.shopmind.dto.AiDtos.ProductData;
import com.shopmind.dto.AiDtos.ProductMatch;
import com.shopmind.dto.AiDtos.QuestionDecision;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Real-AI implementation backed by the Anthropic Java SDK (Claude).
 *
 * Architecture is intentionally hybrid:
 *   - extractIntent / decideNextQuestion → Claude (natural-language strengths)
 *   - rankProducts → MockAiService for deterministic numeric scores, then Claude
 *     rewrites reasoning/tradeoffs/notSuitableFor as a single batch call.
 *
 * Every Claude call is wrapped in try/catch; ANY failure (timeout, malformed JSON,
 * empty key, rate limit) silently falls back to {@link MockAiService}. This keeps
 * the HTTP layer healthy regardless of upstream LLM availability.
 */
@Slf4j
public class ClaudeAiService implements AiService {

    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private static final String INTENT_SYSTEM_PROMPT = """
        You are a footwear intent extraction engine. Given a conversation between a shopper and an
        advisor, extract structured buying intent. Respond ONLY with a valid JSON object — no
        markdown, no explanation, no commentary.

        JSON schema (all fields nullable unless marked required):
        {
          "primaryUseCase": string | null,        // e.g. "college daily wear", "running", "gym training", "walking", "casual daily wear"
          "walkingDuration": string | null,       // e.g. "6-8 hours", "3-5 hours", "under 2 hours"
          "budget": number | null,                // maximum price in the user's local currency as a number
          "comfortPriority": number | null,       // 0.0–1.0; null if not mentioned
          "stylePriority": number | null,         // 0.0–1.0; null if not mentioned
          "durabilityPriority": number | null,    // 0.0–1.0; null if not mentioned
          "terrainType": string | null,           // "urban", "trail", "mixed"
          "preferredFit": string | null,          // "wide", "narrow", "standard", "snug"
          "needsVersatility": boolean | null,
          "confidenceScore": number (required),   // 0.0–1.0
          "missingAttributes": string[] (required),
          "contradictions": string[]
        }

        Rules:
        - Do not invent information not present in the conversation.
        - Key attributes are: primaryUseCase, walkingDuration, budget, comfortPriority, stylePriority, preferredFit.
        - confidenceScore = (6 - missingAttributes.length) / 6.0, rounded to 2 decimal places.
        - If the user explicitly declines to give a budget, remove "budget" from missingAttributes.
        """;

    private static final String QUESTION_SYSTEM_PROMPT = """
        You are a friendly footwear advisor deciding what to ask next in a product recommendation
        conversation. Given the current state of a shopper's extracted intent, decide:
        1. If you have enough information to recommend (readyToRecommend: true), or
        2. What single focused question to ask next.

        Respond ONLY with a valid JSON object — no markdown:
        {
          "readyToRecommend": boolean (required),
          "nextQuestion": string | null,
          "reasoning": string (required),
          "confidence": number (required)
        }

        Rules:
        - Ask at most one question per turn — never bundle two questions.
        - If confidenceScore >= 0.65 OR missingAttributes is empty, set readyToRecommend = true.
        - If questionCount >= 8, always set readyToRecommend = true regardless of confidence.
        - Questions must feel natural, conversational, and specific to what's still unknown.
        - Never ask about an attribute already present in the intent.
        - Prioritize asking about: primaryUseCase > walkingDuration > budget > comfortPriority.
        """;

    private static final String REASONING_SYSTEM_PROMPT = """
        You are a footwear advisor generating purchase reasoning for product recommendations.
        Given a shopper's intent and a list of scored shoe matches, write concise, honest, and
        personal reasoning text for each product. Respond ONLY with a valid JSON array of objects:

        [
          {
            "productId": string,
            "reasoning": string,
            "tradeoffs": string,
            "notSuitableFor": string
          }
        ]

        Rules:
        - Reference the shopper's actual stated needs (use case, hours on feet, budget, terrain, style).
        - Be specific and honest — do not hide significant tradeoffs.
        - Keep reasoning under 60 words per product.
        - Use pipe (|) as the separator inside tradeoffs and notSuitableFor.
        - notSuitableFor may be an empty string if no significant concerns exist.
        """;

    private final ObjectMapper objectMapper;
    private final MockAiService fallback;
    private final String apiKey;
    private final String model;
    private final long maxTokens;

    /** Initialized lazily — building the client too early can mask configuration errors. */
    private volatile AnthropicClient cachedClient;

    public ClaudeAiService(String apiKey, String model, long maxTokens,
                           ObjectMapper objectMapper, MockAiService fallback) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public ExtractedIntent extractIntent(List<Map<String, String>> conversationHistory) {
        if (!canCallClaude()) return fallback.extractIntent(conversationHistory);

        try {
            String formattedHistory = formatHistory(conversationHistory);
            String userPrompt = "Conversation history:\n" + formattedHistory
                + "\n\nExtract the shopper's footwear intent.";

            String json = callClaude(INTENT_SYSTEM_PROMPT, userPrompt, 512);
            JsonNode node = objectMapper.readTree(stripFences(json));
            ExtractedIntent intent = objectMapper.treeToValue(node, ExtractedIntent.class);

            // Defensive guards for required fields when Claude returns nulls
            if (intent.getMissingAttributes() == null) intent.setMissingAttributes(List.of());
            if (intent.getContradictions() == null) intent.setContradictions(List.of());
            if (intent.getConfidenceScore() == null) {
                double conf = 1.0 - (intent.getMissingAttributes().size() * 0.15);
                intent.setConfidenceScore(Math.max(0.1, Math.min(1.0, conf)));
            }
            if (intent.getTerrainType() == null) intent.setTerrainType("urban");

            return intent;
        } catch (Exception e) {
            log.warn("Claude intent extraction failed: {} — falling back to mock", e.getMessage());
            return fallback.extractIntent(conversationHistory);
        }
    }

    @Override
    public QuestionDecision decideNextQuestion(ExtractedIntent intent, int questionCount) {
        if (!canCallClaude()) return fallback.decideNextQuestion(intent, questionCount);

        try {
            String userPrompt = buildQuestionPrompt(intent, questionCount);
            String json = callClaude(QUESTION_SYSTEM_PROMPT, userPrompt, 384);
            JsonNode node = objectMapper.readTree(stripFences(json));

            return QuestionDecision.builder()
                .readyToRecommend(node.path("readyToRecommend").asBoolean(false))
                .nextQuestion(node.hasNonNull("nextQuestion") ? node.get("nextQuestion").asText() : null)
                .reasoning(node.path("reasoning").asText(""))
                .confidence(node.hasNonNull("confidence") ? node.get("confidence").asDouble() : intent.getConfidenceScore())
                .missingAttributes(intent.getMissingAttributes() != null ? intent.getMissingAttributes() : List.of())
                .build();
        } catch (Exception e) {
            log.warn("Claude question decision failed: {} — falling back to mock", e.getMessage());
            return fallback.decideNextQuestion(intent, questionCount);
        }
    }

    @Override
    public List<ProductMatch> rankProducts(List<ProductData> products, ExtractedIntent intent) {
        // Always compute deterministic numeric scores via mock first
        List<ProductMatch> scored = fallback.rankProducts(products, intent);
        if (!canCallClaude() || scored.isEmpty()) return scored;

        try {
            String userPrompt = buildReasoningPrompt(scored, intent);
            String json = callClaude(REASONING_SYSTEM_PROMPT, userPrompt, 1024);
            JsonNode reasoningArray = objectMapper.readTree(stripFences(json));
            if (!reasoningArray.isArray()) {
                log.debug("Claude reasoning response was not an array — keeping mock reasoning");
                return scored;
            }

            Map<String, JsonNode> reasoningMap = new HashMap<>();
            for (JsonNode item : reasoningArray) {
                String id = item.path("productId").asText("");
                if (!id.isEmpty()) reasoningMap.put(id, item);
            }

            return scored.stream().map(match -> {
                JsonNode r = reasoningMap.get(match.getProductId());
                if (r == null) return match;
                return ProductMatch.builder()
                    .productId(match.getProductId())
                    .productName(match.getProductName())
                    .brand(match.getBrand())
                    .imageUrl(match.getImageUrl())
                    .price(match.getPrice())
                    .matchScore(match.getMatchScore())
                    .comfortScore(match.getComfortScore())
                    .durabilityScore(match.getDurabilityScore())
                    .styleScore(match.getStyleScore())
                    .reasoning(textOrFallback(r, "reasoning", match.getReasoning()))
                    .tradeoffs(textOrFallback(r, "tradeoffs", match.getTradeoffs()))
                    .notSuitableFor(textOrFallback(r, "notSuitableFor", match.getNotSuitableFor()))
                    .regretFlags(match.getRegretFlags())
                    .merchants(match.getMerchants())
                    .build();
            }).toList();
        } catch (Exception e) {
            log.warn("Claude reasoning enrichment failed: {} — using mock reasoning", e.getMessage());
            return scored;
        }
    }

    // ─── Claude API plumbing ─────────────────────────────────────────────────

    private boolean canCallClaude() {
        return apiKey != null && !apiKey.isBlank();
    }

    private AnthropicClient getClient() {
        AnthropicClient c = cachedClient;
        if (c == null) {
            synchronized (this) {
                c = cachedClient;
                if (c == null) {
                    c = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                    cachedClient = c;
                }
            }
        }
        return c;
    }

    private String callClaude(String systemPrompt, String userPrompt, long tokenBudget) {
        MessageCreateParams params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(Math.min(tokenBudget, maxTokens))
            .system(systemPrompt)
            .addUserMessage(userPrompt)
            .build();

        Message response = getClient().messages().create(params);
        List<ContentBlock> blocks = response.content();
        return blocks.stream()
            .map(ContentBlock::text)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(TextBlock::text)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No text content in Claude response"));
    }

    // ─── Prompt builders ─────────────────────────────────────────────────────

    private String formatHistory(List<Map<String, String>> history) {
        return history.stream()
            .map(m -> "[" + capitalize(m.getOrDefault("role", "user")) + "]: "
                + m.getOrDefault("content", ""))
            .collect(Collectors.joining("\n"));
    }

    private String buildQuestionPrompt(ExtractedIntent intent, int questionCount) {
        StringBuilder sb = new StringBuilder("Current intent state:\n");
        sb.append("- primaryUseCase: ").append(orUnknown(intent.getPrimaryUseCase())).append("\n");
        sb.append("- walkingDuration: ").append(orUnknown(intent.getWalkingDuration())).append("\n");
        sb.append("- budget: ").append(orUnknown(intent.getBudget())).append("\n");
        sb.append("- comfortPriority: ").append(orUnknown(intent.getComfortPriority())).append("\n");
        sb.append("- stylePriority: ").append(orUnknown(intent.getStylePriority())).append("\n");
        sb.append("- durabilityPriority: ").append(orUnknown(intent.getDurabilityPriority())).append("\n");
        sb.append("- preferredFit: ").append(orUnknown(intent.getPreferredFit())).append("\n");
        sb.append("- terrainType: ").append(orUnknown(intent.getTerrainType())).append("\n");
        sb.append("- needsVersatility: ").append(orUnknown(intent.getNeedsVersatility())).append("\n");
        sb.append("- confidenceScore: ").append(orUnknown(intent.getConfidenceScore())).append("\n");
        sb.append("- missingAttributes: ").append(
            intent.getMissingAttributes() != null ? intent.getMissingAttributes() : List.of()).append("\n");
        sb.append("- questionCount: ").append(questionCount).append("\n\n");
        sb.append("Decide what to do next.");
        return sb.toString();
    }

    private String buildReasoningPrompt(List<ProductMatch> matches, ExtractedIntent intent) {
        try {
            List<Map<String, Object>> compact = matches.stream().map(m -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("productId", m.getProductId());
                entry.put("productName", m.getProductName());
                entry.put("brand", m.getBrand());
                entry.put("price", m.getPrice());
                entry.put("matchScore", m.getMatchScore());
                entry.put("comfortScore", m.getComfortScore());
                entry.put("durabilityScore", m.getDurabilityScore());
                entry.put("styleScore", m.getStyleScore());
                return entry;
            }).toList();

            String intentSummary = "Shopper's needs:\n"
                + "- Use case: " + orUnknown(intent.getPrimaryUseCase()) + "\n"
                + "- Hours on feet: " + orUnknown(intent.getWalkingDuration()) + "\n"
                + "- Budget: " + (intent.getBudget() != null ? "$" + intent.getBudget().intValue() : "unspecified") + "\n"
                + "- Comfort priority: " + orUnknown(intent.getComfortPriority()) + "\n"
                + "- Style priority: " + orUnknown(intent.getStylePriority()) + "\n"
                + "- Durability priority: " + orUnknown(intent.getDurabilityPriority()) + "\n"
                + "- Terrain: " + orUnknown(intent.getTerrainType()) + "\n"
                + "- Needs versatility: " + orUnknown(intent.getNeedsVersatility()) + "\n";

            return intentSummary
                + "\nProducts to reason about:\n"
                + objectMapper.writeValueAsString(compact)
                + "\n\nWrite honest, specific reasoning for each product.";
        } catch (Exception e) {
            // Should never happen — Jackson can serialize the compact map shape above.
            throw new IllegalStateException("Failed to build reasoning prompt", e);
        }
    }

    // ─── Small utilities ─────────────────────────────────────────────────────

    /** Strip ```json fences if Claude wraps its output despite instructions. */
    private String stripFences(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        Matcher m = JSON_FENCE.matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        return trimmed;
    }

    private String textOrFallback(JsonNode node, String key, String fallbackValue) {
        if (node == null || !node.hasNonNull(key)) return fallbackValue;
        String value = node.get(key).asText("");
        return value.isEmpty() ? fallbackValue : value;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String orUnknown(Object v) {
        return v == null ? "unknown" : String.valueOf(v);
    }
}
