package com.shopmind.controller;

import com.shopmind.dto.ConversationResponse;
import com.shopmind.dto.ConversationResponse.RecommendationDTO;
import com.shopmind.dto.MessageRequest;
import com.shopmind.dto.SessionResponse;
import com.shopmind.service.conversation.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createSession(
            @RequestBody(required = false) Map<String, String> body) {
        String guestId = body != null ? body.get("guestId") : null;
        var session = conversationService.createSession(guestId);
        return ResponseEntity.ok(Map.of(
            "sessionId", session.getId().toString(),
            "guestId",   session.getGuestId()
        ));
    }

    @GetMapping("/history/{guestId}")
    public ResponseEntity<List<RecommendationDTO>> getRecommendationHistory(
            @PathVariable String guestId) {
        return ResponseEntity.ok(conversationService.getRecommendationHistory(guestId));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ConversationResponse> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody MessageRequest request) {
        var response = conversationService.processMessage(sessionId, request.getContent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable UUID sessionId) {
        var response = conversationService.getSession(sessionId);
        return ResponseEntity.ok(response);
    }
}
