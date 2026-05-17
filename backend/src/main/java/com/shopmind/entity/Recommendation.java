package com.shopmind.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recommendations")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ConversationSession session;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private String productName;

    private String productBrand;
    private String productImageUrl;

    @Builder.Default
    private Double matchScore = 0.0;

    @Builder.Default
    private Integer comfortScore = 0;

    @Builder.Default
    private Integer durabilityScore = 0;

    @Builder.Default
    private Integer styleScore = 0;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(columnDefinition = "TEXT")
    private String tradeoffs;

    @Column(columnDefinition = "TEXT")
    private String notSuitableFor;

    private Double price;
    private String currency;

    @Builder.Default
    private Integer rank = 0;

    @OneToMany(mappedBy = "recommendation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RegretFlag> regretFlags = new ArrayList<>();

    @OneToMany(mappedBy = "recommendation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MerchantOffer> merchantOffers = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;
}
