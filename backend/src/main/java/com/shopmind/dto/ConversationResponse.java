package com.shopmind.dto;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ConversationResponse {
    private String sessionId;
    private MessageDTO assistantMessage;
    private IntentDTO currentIntent;
    private List<RecommendationDTO> recommendations;
    private DebugInfo debugInfo;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MessageDTO {
        private String id;
        private String role;
        private String content;
        private String reasoningStatus;
        private String createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IntentDTO {
        private String primaryUseCase;
        private String walkingDuration;
        private Double budget;
        private Double comfortPriority;
        private Double stylePriority;
        private Double durabilityPriority;
        private String terrainType;
        private String preferredFit;
        private Boolean needsVersatility;
        private Double confidenceScore;
        private List<String> missingAttributes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecommendationDTO {
        private String id;
        private String productId;
        private String productName;
        private String productBrand;
        private String productImageUrl;
        private Double matchScore;
        private Integer comfortScore;
        private Integer durabilityScore;
        private Integer styleScore;
        private String reasoning;
        private String tradeoffs;
        private String notSuitableFor;
        private Double price;
        private String currency;
        private Integer rank;
        private List<RegretFlagDTO> regretFlags;
        private List<MerchantOfferDTO> merchantOffers;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RegretFlagDTO {
        private String type;
        private String title;
        private String description;
        private String severity;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MerchantOfferDTO {
        private String merchantName;
        private Double price;
        private String currency;
        private String deliveryEstimate;
        private String returnPolicy;
        private Double shippingCost;
        private Boolean inStock;
        private String checkoutUrl;
        private Boolean bestValue;
        private String whyRecommended;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DebugInfo {
        private Long processingTimeMs;
    }
}
