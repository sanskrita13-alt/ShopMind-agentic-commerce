package com.shopmind.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.dto.AiDtos;
import com.shopmind.dto.AiDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mock AI service that simulates the conversational intelligence pipeline using deterministic
 * keyword matching and rule-based scoring. Used when shopmind.ai.mock-mode=true (default) or as
 * a fallback when the real Claude implementation fails.
 *
 * Scoring leverages all extracted intent attributes:
 * stylePriority, durabilityPriority, needsVersatility, terrainType, comfortPriority, budget.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockAiService implements AiService {

    private static final List<String> KEY_ATTRIBUTES = List.of(
        "primaryUseCase", "walkingDuration", "budget",
        "comfortPriority", "stylePriority", "preferredFit"
    );

    private static final List<String> VERSATILE_TAGS = List.of(
        "versatile", "lifestyle", "daily", "all-day", "casual"
    );

    private static final List<String> STYLE_TAGS = List.of(
        "stylish", "classic", "minimalist", "retro", "style"
    );

    private final ObjectMapper objectMapper;

    @Value("${shopmind.ai.mock-mode:true}")
    private boolean mockMode;

    public boolean isMockMode() {
        return mockMode;
    }

    @Override
    public ExtractedIntent extractIntent(List<Map<String, String>> conversationHistory) {
        String allMessages = conversationHistory.stream()
            .filter(m -> "user".equals(m.get("role")))
            .map(m -> m.get("content"))
            .reduce("", (a, b) -> a + " " + b)
            .toLowerCase();

        var builder = ExtractedIntent.builder();
        List<String> missing = new ArrayList<>(KEY_ATTRIBUTES);

        // Use case
        if (allMessages.contains("college") || allMessages.contains("campus") || allMessages.contains("university")) {
            builder.primaryUseCase("college daily wear");
            missing.remove("primaryUseCase");
        } else if (allMessages.contains("gym") || allMessages.contains("workout") || allMessages.contains("training")) {
            builder.primaryUseCase("gym training");
            missing.remove("primaryUseCase");
        } else if (allMessages.contains("run") || allMessages.contains("jog")) {
            builder.primaryUseCase("running");
            missing.remove("primaryUseCase");
        } else if (allMessages.contains("walk")) {
            builder.primaryUseCase("walking");
            missing.remove("primaryUseCase");
        } else if (allMessages.contains("casual") || allMessages.contains("everyday") || allMessages.contains("daily")) {
            builder.primaryUseCase("casual daily wear");
            missing.remove("primaryUseCase");
        }

        // Walking duration
        if (allMessages.contains("6") || allMessages.contains("7") || allMessages.contains("8")) {
            builder.walkingDuration("6-8 hours");
            missing.remove("walkingDuration");
        } else if (allMessages.contains("3") || allMessages.contains("4") || allMessages.contains("5")) {
            builder.walkingDuration("3-5 hours");
            missing.remove("walkingDuration");
        }

        // Budget (first standalone number between 50 and 500)
        if (allMessages.matches(".*\\d{2,4}.*")) {
            String nums = allMessages.replaceAll("[^0-9 ]", " ");
            for (String n : nums.split("\\s+")) {
                if (n.isBlank()) continue;
                try {
                    double val = Double.parseDouble(n);
                    if (val >= 50 && val <= 500) {
                        builder.budget(val);
                        missing.remove("budget");
                        break;
                    }
                } catch (NumberFormatException ignored) {
                    // skip non-numeric tokens
                }
            }
        }

        // Priorities
        if (allMessages.contains("comfort")) {
            builder.comfortPriority(0.9);
            missing.remove("comfortPriority");
        }
        if (allMessages.contains("style") || allMessages.contains("look")) {
            builder.stylePriority(0.8);
            missing.remove("stylePriority");
        }
        if (allMessages.contains("durable") || allMessages.contains("last") || allMessages.contains("long lasting")) {
            builder.durabilityPriority(0.8);
        }

        // Fit
        if (allMessages.contains("wide")) {
            builder.preferredFit("wide");
            missing.remove("preferredFit");
        } else if (allMessages.contains("narrow")) {
            builder.preferredFit("narrow");
            missing.remove("preferredFit");
        }

        // Versatility
        if (allMessages.contains("versatile") || allMessages.contains("both") || allMessages.contains(" and ")) {
            builder.needsVersatility(true);
        }

        // Terrain
        if (allMessages.contains("trail") || allMessages.contains("outdoor") || allMessages.contains("off-road")) {
            builder.terrainType("trail");
        } else {
            builder.terrainType("urban");
        }

        double confidence = 1.0 - (missing.size() * 0.15);
        builder.confidenceScore(Math.max(0.1, Math.min(1.0, confidence)));
        builder.missingAttributes(missing);
        builder.contradictions(List.of());

        return builder.build();
    }

    /**
     * Decide whether to recommend or ask another question.
     *
     * GAP 5 FIX: previously forced readyToRecommend at questionCount >= 5 regardless of confidence,
     * which produced poor recommendations from very low-confidence intents. New logic:
     *   - Ready if no missing attributes, OR
     *   - Ready if questionCount >= 5 AND confidence >= 0.4 (sufficient signal collected), OR
     *   - Ready if questionCount >= 8 (hard cap to prevent infinite loops)
     */
    @Override
    public QuestionDecision decideNextQuestion(ExtractedIntent intent, int questionCount) {
        boolean sufficientInfo = intent.getMissingAttributes() == null || intent.getMissingAttributes().isEmpty();
        boolean highConfidence = intent.getConfidenceScore() != null && intent.getConfidenceScore() >= 0.4;
        boolean hardCap = questionCount >= 8;

        if (sufficientInfo || (questionCount >= 5 && highConfidence) || hardCap) {
            return QuestionDecision.builder()
                .readyToRecommend(true)
                .confidence(intent.getConfidenceScore())
                .missingAttributes(List.of())
                .build();
        }

        String nextAttr = intent.getMissingAttributes().get(0);
        String question;
        String reasoning;

        switch (nextAttr) {
            case "primaryUseCase" -> {
                question = "What will you primarily use these shoes for? For example: college, gym, running, walking, or casual everyday wear?";
                reasoning = "Understanding primary use case is essential to recommend the right shoe category.";
            }
            case "walkingDuration" -> {
                question = "How many hours are you typically on your feet each day?";
                reasoning = "Walking duration directly affects the cushioning and support level needed.";
            }
            case "budget" -> {
                question = "What budget range are you comfortable with? This helps me find the best value options.";
                reasoning = "Budget determines which product tier and merchants to prioritize.";
            }
            case "comfortPriority" -> {
                question = "What matters more to you overall — comfort or style?";
                reasoning = "This tradeoff shapes whether I prioritize cushioning technology or design aesthetics.";
            }
            case "stylePriority" -> {
                question = "How important is the shoe's appearance for your daily outfits?";
                reasoning = "Style priority helps balance between performance features and visual appeal.";
            }
            case "preferredFit" -> {
                question = "Do you have any fit preferences? For example, do you prefer a wider toe box or a snug racing fit?";
                reasoning = "Fit preferences help prevent sizing regret, which is the #1 return reason.";
            }
            default -> {
                question = "Is there anything else you'd like me to consider for your recommendation?";
                reasoning = "Gathering additional context to refine the recommendation.";
            }
        }

        return QuestionDecision.builder()
            .readyToRecommend(false)
            .nextQuestion(question)
            .reasoning(reasoning)
            .missingAttributes(intent.getMissingAttributes())
            .confidence(intent.getConfidenceScore())
            .build();
    }

    @Override
    public List<ProductMatch> rankProducts(List<ProductData> products, ExtractedIntent intent) {
        return products.stream()
            .map(p -> scoreProduct(p, intent))
            .sorted(Comparator.comparingDouble(ProductMatch::getMatchScore).reversed())
            .limit(3)
            .toList();
    }

    /**
     * Score a single product against the intent.
     *
     * Scoring components (all gaps from plan addressed):
     *   - Cushioning vs comfort priority
     *   - Use case tag match
     *   - Budget fit
     *   - Long-duration regret flags
     *   - GAP 1: stylePriority now scales styleScore + matchScore
     *   - GAP 2: durabilityPriority uses material + price proxy (no brand hardcoding)
     *   - GAP 3: needsVersatility checks lifestyle tags
     *   - GAP 4: terrainType checks product.attributes["terrain"]
     */
    private ProductMatch scoreProduct(ProductData product, ExtractedIntent intent) {
        double score = 0.5;
        int comfort = 70;
        int durability;
        int style;
        List<String> tradeoffList = new ArrayList<>();
        List<String> notSuitableList = new ArrayList<>();
        List<RegretAnalysis> regrets = new ArrayList<>();

        Map<String, Object> attrs = product.getAttributes() != null ? product.getAttributes() : Map.of();
        String cushioning = String.valueOf(attrs.getOrDefault("cushioning", "moderate"));
        String material = String.valueOf(attrs.getOrDefault("material", ""));
        String productTerrain = String.valueOf(attrs.getOrDefault("terrain", "urban"));
        List<String> tags = product.getTags() != null ? product.getTags() : List.of();
        String productType = product.getProductType() != null ? product.getProductType() : "";

        // --- Cushioning vs comfort priority ---
        if (intent.getComfortPriority() != null && intent.getComfortPriority() > 0.7) {
            if ("ultra-high".equals(cushioning) || "maximum".equals(cushioning)) {
                score += 0.2;
                comfort = 95;
            } else if ("high".equals(cushioning)) {
                score += 0.15;
                comfort = 85;
            } else {
                score -= 0.1;
                comfort = 60;
                tradeoffList.add("Moderate cushioning may not be ideal for all-day comfort needs");
            }
        }

        // --- Use case tag match ---
        if (intent.getPrimaryUseCase() != null) {
            String useCaseLower = intent.getPrimaryUseCase().toLowerCase();
            boolean matches = tags.stream().anyMatch(t -> useCaseLower.contains(t.toLowerCase()));
            if (matches) {
                score += 0.15;
            } else {
                tradeoffList.add("Not specifically designed for " + intent.getPrimaryUseCase());
            }
        }

        // --- Budget fit ---
        if (intent.getBudget() != null && product.getMinPrice() != null) {
            if (product.getMinPrice() <= intent.getBudget()) {
                score += 0.1;
            } else {
                score -= 0.1;
                tradeoffList.add("Above your stated budget of $" + intent.getBudget().intValue());
            }
        }

        // --- Walking duration regret flags ---
        if (intent.getWalkingDuration() != null && intent.getWalkingDuration().contains("6")) {
            if ("low".equals(cushioning) || "moderate".equals(cushioning)) {
                regrets.add(RegretAnalysis.builder()
                    .type("CUSHIONING")
                    .title("Limited cushioning for long hours")
                    .description("With 6-8 hours on your feet daily, this shoe's " + cushioning
                        + " cushioning may become uncomfortable by afternoon.")
                    .severity("HIGH").build());
            }
            String weight = String.valueOf(attrs.getOrDefault("weight", "300g"));
            try {
                int w = Integer.parseInt(weight.replace("g", "").trim());
                if (w > 350) {
                    regrets.add(RegretAnalysis.builder()
                        .type("WEIGHT")
                        .title("Heavier than ideal for extended wear")
                        .description("At " + weight + ", this shoe may cause foot fatigue during long days.")
                        .severity("MEDIUM").build());
                }
            } catch (NumberFormatException ignored) {
                // unparseable weight string — skip
            }
        }

        // --- GAP 2: Durability using material + price proxy, scaled by priority ---
        int baseDurability = 70;
        if ("leather".equalsIgnoreCase(material)) {
            baseDurability = 88;
        }
        if (product.getMinPrice() != null) {
            if (product.getMinPrice() >= 140) baseDurability += 5;
            if (product.getMinPrice() >= 170) baseDurability += 3;
        }
        durability = baseDurability;
        if (intent.getDurabilityPriority() != null && intent.getDurabilityPriority() > 0.6) {
            double dp = intent.getDurabilityPriority();
            score += 0.1 * dp * (baseDurability / 100.0);
            if (baseDurability < 75) {
                tradeoffList.add("Durability may not meet your priority requirements");
            }
        }

        // --- GAP 1: Style using stylePriority ---
        int baseStyle = 65;
        if (tags.stream().anyMatch(STYLE_TAGS::contains)) {
            baseStyle = 82;
        }
        if (productType.contains("Casual") || productType.contains("Lifestyle")) {
            baseStyle += 8;
        }
        if (intent.getStylePriority() != null && intent.getStylePriority() > 0.6) {
            double sp = intent.getStylePriority();
            score += 0.12 * sp;
            style = Math.min(99, (int) (baseStyle + 15 * sp));
        } else {
            style = baseStyle;
        }

        // --- GAP 3: needsVersatility ---
        if (Boolean.TRUE.equals(intent.getNeedsVersatility())) {
            boolean isVersatile = tags.stream().anyMatch(VERSATILE_TAGS::contains);
            if (isVersatile) {
                score += 0.1;
            } else if (productType.contains("Running")) {
                score -= 0.05;
                tradeoffList.add("Primarily a performance shoe, limited versatility for casual use");
            }
        }

        // --- GAP 4: terrainType vs product terrain ---
        if (intent.getTerrainType() != null) {
            String intentTerrain = intent.getTerrainType().toLowerCase();
            if ("trail".equals(intentTerrain) || "outdoor".equals(intentTerrain)) {
                if ("trail".equals(productTerrain) || "off-road".equals(productTerrain)) {
                    score += 0.1;
                } else {
                    score -= 0.08;
                    notSuitableList.add("Designed for road/urban surfaces, not trail use");
                }
            } else {
                if ("trail".equals(productTerrain)) {
                    score -= 0.05;
                    tradeoffList.add("Trail-specific outsole may feel stiff on hard urban surfaces");
                }
            }
        }

        // Casual mismatch warning for pure running shoes
        if (tags.contains("running") && intent.getPrimaryUseCase() != null
            && intent.getPrimaryUseCase().toLowerCase().contains("casual")) {
            notSuitableList.add("May look too athletic for pure casual/streetwear styling");
        }

        score = Math.max(0.3, Math.min(0.99, score));

        return ProductMatch.builder()
            .productId(product.getId())
            .productName(product.getTitle())
            .brand(product.getBrand())
            .imageUrl(product.getImages() == null || product.getImages().isEmpty() ? null : product.getImages().get(0))
            .price(product.getMinPrice())
            .matchScore(Math.round(score * 100.0) / 100.0)
            .comfortScore(comfort)
            .durabilityScore(Math.min(99, durability))
            .styleScore(Math.min(99, style))
            .reasoning(generateReasoning(product, intent, score))
            .tradeoffs(String.join(" | ", tradeoffList))
            .notSuitableFor(String.join(" | ", notSuitableList))
            .regretFlags(regrets)
            .merchants(List.of())
            .build();
    }

    /**
     * Build contextual reasoning text incorporating all relevant intent dimensions.
     * GAP 6 FIX: includes style, terrain, and versatility commentary when relevant.
     */
    private String generateReasoning(AiDtos.ProductData product, ExtractedIntent intent, double score) {
        StringBuilder sb = new StringBuilder();
        sb.append("The ").append(product.getTitle()).append(" by ").append(product.getBrand());

        if (score > 0.8) {
            sb.append(" is an excellent match for your needs. ");
        } else if (score > 0.6) {
            sb.append(" is a solid option worth considering. ");
        } else {
            sb.append(" could work, but has some tradeoffs to consider. ");
        }

        if (intent.getPrimaryUseCase() != null) {
            sb.append("For ").append(intent.getPrimaryUseCase()).append(", ");
            String cushioning = String.valueOf(
                (product.getAttributes() != null ? product.getAttributes() : Map.of())
                    .getOrDefault("cushioning", "moderate"));
            sb.append("it offers ").append(cushioning).append(" cushioning");
            if (intent.getWalkingDuration() != null) {
                sb.append(" which is important for your ").append(intent.getWalkingDuration()).append(" daily use");
            }
            sb.append(". ");
        }

        // Style commentary
        if (intent.getStylePriority() != null && intent.getStylePriority() > 0.6) {
            List<String> tags = product.getTags() != null ? product.getTags() : List.of();
            boolean isStyled = tags.stream().anyMatch(STYLE_TAGS::contains);
            sb.append(isStyled
                ? "Its aesthetic suits style-conscious buyers. "
                : "Its appearance leans toward performance over fashion. ");
        }

        // Terrain commentary
        if (intent.getTerrainType() != null && product.getAttributes() != null) {
            String terrain = String.valueOf(product.getAttributes().getOrDefault("terrain", "urban"));
            if (!intent.getTerrainType().equalsIgnoreCase(terrain)
                && !("urban".equals(intent.getTerrainType()) && "road".equals(terrain))) {
                sb.append("Note: this shoe is optimized for ").append(terrain).append(" surfaces. ");
            }
        }

        // Versatility commentary
        if (Boolean.TRUE.equals(intent.getNeedsVersatility())) {
            List<String> tags = product.getTags() != null ? product.getTags() : List.of();
            boolean versatile = tags.stream().anyMatch(VERSATILE_TAGS::contains);
            sb.append(versatile
                ? "It handles multiple use contexts well. "
                : "Best used for its primary purpose only. ");
        }

        // Budget commentary
        if (product.getMinPrice() != null) {
            sb.append("At $").append(product.getMinPrice().intValue()).append(", ");
            if (intent.getBudget() != null && product.getMinPrice() <= intent.getBudget()) {
                sb.append("it fits within your budget.");
            } else if (intent.getBudget() != null) {
                sb.append("it's slightly above your target budget but may be worth the investment for the quality.");
            } else {
                sb.append("it's competitively priced for its category.");
            }
        }

        return sb.toString();
    }
}
