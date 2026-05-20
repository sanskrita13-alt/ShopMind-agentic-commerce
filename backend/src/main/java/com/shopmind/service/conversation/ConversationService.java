package com.shopmind.service.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.dto.AiDtos.*;
import com.shopmind.dto.ConversationResponse;
import com.shopmind.dto.ConversationResponse.*;
import com.shopmind.dto.SessionResponse;
import com.shopmind.entity.*;
import com.shopmind.repository.*;
import com.shopmind.service.ai.AiService;
import com.shopmind.service.product.ShopifyProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationSessionRepository sessionRepo;
    private final ConversationMessageRepository messageRepo;
    private final UserIntentRepository intentRepo;
    private final RecommendationRepository recommendationRepo;
    private final AiService aiService;
    private final ShopifyProductService productService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ConversationSession createSession(String guestId) {
        var session = ConversationSession.builder()
            .guestId(guestId != null && !guestId.isBlank() ? guestId : UUID.randomUUID().toString())
            .status(ConversationSession.SessionStatus.ACTIVE)
            .build();

        session = sessionRepo.save(session);

        var greeting = ConversationMessage.builder()
            .session(session)
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .content("Welcome to ShopMind. I'm your AI footwear advisor — I'll help you find the right shoes by understanding your needs, explaining tradeoffs, and preventing purchase regret. What are you looking for today?")
            .reasoningStatus("ready")
            .build();
        messageRepo.save(greeting);

        return session;
    }

    public List<RecommendationDTO> getRecommendationHistory(String guestId) {
        return recommendationRepo.findBySession_GuestIdOrderByCreatedAtDesc(guestId)
            .stream().map(this::toRecommendationDTO).toList();
    }

    @Transactional
    public ConversationResponse processMessage(UUID sessionId, String userMessage) {
        long startTime = System.currentTimeMillis();
        var session = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        // Save user message
        var userMsg = ConversationMessage.builder()
            .session(session)
            .role(ConversationMessage.MessageRole.USER)
            .content(userMessage)
            .build();
        messageRepo.save(userMsg);

        // Build conversation history for AI
        var messages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<Map<String, String>> history = messages.stream()
            .map(m -> Map.of("role", m.getRole().name().toLowerCase(), "content", m.getContent()))
            .collect(Collectors.toList());

        // Step 1: Extract intent
        ExtractedIntent intent = aiService.extractIntent(history);
        log.debug("Extracted intent: {}", intent);

        // Save intent version
        var previousIntent = intentRepo.findTopBySessionIdOrderByVersionDesc(sessionId);
        int nextVersion = previousIntent.map(i -> i.getVersion() + 1).orElse(1);
        try {
            var intentEntity = UserIntent.builder()
                .session(session)
                .version(nextVersion)
                .intentJson(objectMapper.writeValueAsString(intent))
                .confidence(intent.getConfidenceScore())
                .missingAttributesJson(objectMapper.writeValueAsString(intent.getMissingAttributes()))
                .contradictionsJson(objectMapper.writeValueAsString(intent.getContradictions()))
                .build();
            intentRepo.save(intentEntity);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize intent", e);
        }

        // Update session
        session.setConfidenceScore(intent.getConfidenceScore());
        session.setQuestionCount(session.getQuestionCount() + 1);
        try {
            session.setCurrentIntentJson(objectMapper.writeValueAsString(intent));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session intent", e);
        }

        // Step 2: Decide next action
        QuestionDecision decision = aiService.decideNextQuestion(intent, session.getQuestionCount());

        String assistantContent;
        String reasoningStatus;
        List<RecommendationDTO> recommendationDTOs = List.of();

        if (decision.isReadyToRecommend()) {
            // Step 3: Retrieve and rank products
            reasoningStatus = "Analyzing your requirements and matching products…";

            List<String> useCaseTags = deriveTagsFromIntent(intent);
            Double strictBudget = intent.getBudget();
            var products = productService.searchProducts(intent.getPrimaryUseCase(), strictBudget, useCaseTags);

            // If the tag-filtered list is empty (but budget is not the blocker), widen to full catalog
            // under the same strict budget ceiling — never exceed the user's stated budget.
            if (products.isEmpty()) {
                products = productService.searchProducts(null, strictBudget, null);
            }

            // Still empty → nothing in the catalog fits the budget; tell the user clearly.
            if (products.isEmpty() && strictBudget != null) {
                String budgetStr = "₹" + strictBudget.intValue();
                assistantContent = "Hmm, I searched the full catalog and couldn't find any shoes under " + budgetStr
                    + " that match your needs. You may want to revise your budget — let me know if you'd like to explore options at a higher price point.";
                reasoningStatus = "No products found within budget";

                var assistantMsg = ConversationMessage.builder()
                    .session(session)
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .content(assistantContent)
                    .reasoningStatus(reasoningStatus)
                    .build();
                messageRepo.save(assistantMsg);
                sessionRepo.save(session);

                long processingTime = System.currentTimeMillis() - startTime;
                return ConversationResponse.builder()
                    .sessionId(sessionId.toString())
                    .assistantMessage(MessageDTO.builder()
                        .id(assistantMsg.getId() != null ? assistantMsg.getId().toString() : UUID.randomUUID().toString())
                        .role("assistant")
                        .content(assistantContent)
                        .reasoningStatus(reasoningStatus)
                        .build())
                    .currentIntent(toIntentDTO(intent))
                    .recommendations(List.of())
                    .debugInfo(DebugInfo.builder().processingTimeMs(processingTime).build())
                    .build();
            }

            var ranked = aiService.rankProducts(products, intent);

            // Save recommendations
            List<Recommendation> savedRecs = new ArrayList<>();
            for (int i = 0; i < ranked.size(); i++) {
                var match = ranked.get(i);
                var merchants = productService.getMerchantOffers(match.getProductId());

                final String productId = match.getProductId();
                var matchedProduct = products.stream()
                    .filter(p -> p.getId().equals(productId))
                    .findFirst()
                    .orElseGet(() -> productService.getProduct(productId).orElse(null));
                String currency = (matchedProduct != null && matchedProduct.getCurrency() != null)
                    ? matchedProduct.getCurrency()
                    : "USD";

                var rec = Recommendation.builder()
                    .session(session)
                    .productId(match.getProductId())
                    .productName(match.getProductName())
                    .productBrand(match.getBrand())
                    .productImageUrl(match.getImageUrl())
                    .matchScore(match.getMatchScore())
                    .comfortScore(match.getComfortScore())
                    .durabilityScore(match.getDurabilityScore())
                    .styleScore(match.getStyleScore())
                    .reasoning(match.getReasoning())
                    .tradeoffs(match.getTradeoffs())
                    .notSuitableFor(match.getNotSuitableFor())
                    .price(match.getPrice())
                    .currency(currency)
                    .rank(i + 1)
                    .build();

                // Add regret flags
                for (var rf : match.getRegretFlags()) {
                    rec.getRegretFlags().add(RegretFlag.builder()
                        .recommendation(rec)
                        .type(RegretFlag.FlagType.valueOf(rf.getType()))
                        .title(rf.getTitle())
                        .description(rf.getDescription())
                        .severity(RegretFlag.Severity.valueOf(rf.getSeverity()))
                        .build());
                }

                // Add merchant offers
                for (var m : merchants) {
                    rec.getMerchantOffers().add(MerchantOffer.builder()
                        .recommendation(rec)
                        .merchantName(m.getName())
                        .price(m.getPrice())
                        .currency(currency)
                        .deliveryEstimate(m.getDelivery())
                        .returnPolicy(m.getReturnPolicy())
                        .shippingCost(m.getShipping())
                        .inStock(m.getInStock())
                        .checkoutUrl(m.getUrl())
                        .bestValue(m.getBestValue())
                        .whyRecommended(m.getReason())
                        .build());
                }

                savedRecs.add(rec);
            }
            recommendationRepo.saveAll(savedRecs);
            session.setRecommendationsGenerated(true);

            // Generate response text
            assistantContent = generateRecommendationResponse(ranked, intent);
            recommendationDTOs = savedRecs.stream().map(this::toRecommendationDTO).toList();
            reasoningStatus = "Recommendations ready";

        } else {
            // Ask next question
            assistantContent = decision.getNextQuestion();
            reasoningStatus = decision.getReasoning();
        }

        // Save assistant message
        var assistantMsg = ConversationMessage.builder()
            .session(session)
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .content(assistantContent)
            .reasoningStatus(reasoningStatus)
            .build();
        messageRepo.save(assistantMsg);
        sessionRepo.save(session);

        long processingTime = System.currentTimeMillis() - startTime;

        return ConversationResponse.builder()
            .sessionId(sessionId.toString())
            .assistantMessage(MessageDTO.builder()
                .id(assistantMsg.getId() != null ? assistantMsg.getId().toString() : UUID.randomUUID().toString())
                .role("assistant")
                .content(assistantContent)
                .reasoningStatus(reasoningStatus)
                .build())
            .currentIntent(toIntentDTO(intent))
            .recommendations(recommendationDTOs)
            .debugInfo(DebugInfo.builder()
                .processingTimeMs(processingTime)
                .build())
            .build();
    }

    public SessionResponse getSession(UUID sessionId) {
        var session = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        var messages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        var recommendations = recommendationRepo.findBySessionIdOrderByRankAsc(sessionId);

        ExtractedIntent intent = null;
        if (session.getCurrentIntentJson() != null) {
            try {
                intent = objectMapper.readValue(session.getCurrentIntentJson(), ExtractedIntent.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse intent JSON", e);
            }
        }

        return SessionResponse.builder()
            .sessionId(sessionId.toString())
            .status(session.getStatus().name())
            .confidenceScore(session.getConfidenceScore())
            .questionCount(session.getQuestionCount())
            .recommendationsGenerated(session.getRecommendationsGenerated())
            .messages(messages.stream().map(m -> MessageDTO.builder()
                .id(m.getId().toString())
                .role(m.getRole().name().toLowerCase())
                .content(m.getContent())
                .reasoningStatus(m.getReasoningStatus())
                .createdAt(m.getCreatedAt() != null ? m.getCreatedAt().toString() : null)
                .build()).toList())
            .currentIntent(intent != null ? toIntentDTO(intent) : null)
            .recommendations(recommendations.stream().map(this::toRecommendationDTO).toList())
            .createdAt(session.getCreatedAt() != null ? session.getCreatedAt().toString() : null)
            .build();
    }

    /**
     * Derive a list of catalog tags from the user's primary use case and terrain, used to
     * pre-filter the product list before scoring. Keeps the ranker focused on a smaller,
     * more relevant candidate set.
     */
    private List<String> deriveTagsFromIntent(ExtractedIntent intent) {
        List<String> tags = new ArrayList<>();
        if (intent.getPrimaryUseCase() != null) {
            String uc = intent.getPrimaryUseCase().toLowerCase();
            if (uc.contains("run")) tags.add("running");
            if (uc.contains("gym") || uc.contains("train")) {
                tags.add("gym");
                tags.add("training");
            }
            if (uc.contains("walk")) tags.add("walking");
            if (uc.contains("casual") || uc.contains("daily") || uc.contains("college") || uc.contains("everyday")) {
                tags.add("casual");
                tags.add("college");
                tags.add("daily");
            }
        }
        if (intent.getTerrainType() != null && "trail".equalsIgnoreCase(intent.getTerrainType())) {
            tags.add("trail");
        }
        return tags;
    }

    private String generateRecommendationResponse(List<ProductMatch> matches, ExtractedIntent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("Alright — I've got a clear enough picture.");
        if (intent.getPrimaryUseCase() != null) {
            sb.append(" ").append(capitalize(intent.getPrimaryUseCase())).append(",");
        }
        if (intent.getWalkingDuration() != null) {
            sb.append(" ").append(intent.getWalkingDuration()).append(" on your feet,");
        }
        if (intent.getBudget() != null) {
            sb.append(" ₹").append(intent.getBudget().intValue()).append(" budget —");
        }
        sb.append(" here are ").append(matches.size()).append(" option")
          .append(matches.size() != 1 ? "s" : "").append(" I'd actually stand behind");
        if (intent.getBudget() != null) {
            sb.append(", all within your ₹").append(intent.getBudget().intValue()).append(" budget");
        }
        sb.append(".\n\n");
        sb.append("I've ranked them by how well they fit your stated priorities. ");
        sb.append("Tap any card for my full reasoning, tradeoffs, and where to buy.");
        return sb.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private IntentDTO toIntentDTO(ExtractedIntent intent) {
        return IntentDTO.builder()
            .primaryUseCase(intent.getPrimaryUseCase())
            .gender(intent.getGender())
            .walkingDuration(intent.getWalkingDuration())
            .budget(intent.getBudget())
            .comfortPriority(intent.getComfortPriority())
            .stylePriority(intent.getStylePriority())
            .durabilityPriority(intent.getDurabilityPriority())
            .terrainType(intent.getTerrainType())
            .preferredFit(intent.getPreferredFit())
            .needsVersatility(intent.getNeedsVersatility())
            .confidenceScore(intent.getConfidenceScore())
            .missingAttributes(intent.getMissingAttributes())
            .build();
    }

    private RecommendationDTO toRecommendationDTO(Recommendation rec) {
        return RecommendationDTO.builder()
            .id(rec.getId().toString())
            .productId(rec.getProductId())
            .productName(rec.getProductName())
            .productBrand(rec.getProductBrand())
            .productImageUrl(rec.getProductImageUrl())
            .matchScore(rec.getMatchScore())
            .comfortScore(rec.getComfortScore())
            .durabilityScore(rec.getDurabilityScore())
            .styleScore(rec.getStyleScore())
            .reasoning(rec.getReasoning())
            .tradeoffs(rec.getTradeoffs())
            .notSuitableFor(rec.getNotSuitableFor())
            .price(rec.getPrice())
            .currency(rec.getCurrency())
            .rank(rec.getRank())
            .regretFlags(rec.getRegretFlags().stream().map(rf -> RegretFlagDTO.builder()
                .type(rf.getType().name())
                .title(rf.getTitle())
                .description(rf.getDescription())
                .severity(rf.getSeverity().name())
                .build()).toList())
            .merchantOffers(rec.getMerchantOffers().stream().map(mo -> MerchantOfferDTO.builder()
                .merchantName(mo.getMerchantName())
                .price(mo.getPrice())
                .currency(mo.getCurrency())
                .deliveryEstimate(mo.getDeliveryEstimate())
                .returnPolicy(mo.getReturnPolicy())
                .shippingCost(mo.getShippingCost())
                .inStock(mo.getInStock())
                .checkoutUrl(mo.getCheckoutUrl())
                .bestValue(mo.getBestValue())
                .whyRecommended(mo.getWhyRecommended())
                .build()).toList())
            .createdAt(rec.getCreatedAt() != null ? rec.getCreatedAt().toString() : null)
            .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
