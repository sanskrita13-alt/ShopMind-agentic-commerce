package com.shopmind.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_intents")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ConversationSession session;

    @Column(nullable = false)
    private Integer version;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String intentJson;

    @Builder.Default
    private Double confidence = 0.0;

    @Column(columnDefinition = "TEXT")
    private String missingAttributesJson;

    @Column(columnDefinition = "TEXT")
    private String contradictionsJson;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
