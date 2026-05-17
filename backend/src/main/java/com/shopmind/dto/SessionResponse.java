package com.shopmind.dto;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SessionResponse {
    private String sessionId;
    private String status;
    private Double confidenceScore;
    private Integer questionCount;
    private Boolean recommendationsGenerated;
    private List<ConversationResponse.MessageDTO> messages;
    private ConversationResponse.IntentDTO currentIntent;
    private List<ConversationResponse.RecommendationDTO> recommendations;
    private String createdAt;
}
