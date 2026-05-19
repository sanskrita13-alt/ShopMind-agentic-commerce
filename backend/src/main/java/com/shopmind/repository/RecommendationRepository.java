package com.shopmind.repository;

import com.shopmind.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {
    List<Recommendation> findBySessionIdOrderByRankAsc(UUID sessionId);
    List<Recommendation> findBySession_GuestIdOrderByCreatedAtDesc(String guestId);
}
