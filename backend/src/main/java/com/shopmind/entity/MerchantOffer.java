package com.shopmind.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchant_offers")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MerchantOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @Column(nullable = false)
    private String merchantName;

    @Column(nullable = false)
    private Double price;

    private String currency;
    private String deliveryEstimate;
    private String returnPolicy;
    private Double shippingCost;
    private Boolean inStock;
    private String checkoutUrl;

    @Builder.Default
    private Boolean bestValue = false;

    @Column(columnDefinition = "TEXT")
    private String whyRecommended;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
