package com.shopmind.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "regret_flags")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RegretFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlagType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Severity severity = Severity.MEDIUM;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum FlagType {
        CUSHIONING, FIT, DURABILITY, USE_CASE_MISMATCH, SIZING, OVERPRICED, WALKING_SUPPORT, BREATHABILITY, WEIGHT
    }

    public enum Severity {
        LOW, MEDIUM, HIGH
    }
}
