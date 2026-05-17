package com.shopmind.repository;

import com.shopmind.entity.ProductSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for cached Shopify product data.
 * Used by ShopifyProductService to avoid re-fetching products within the configured TTL window.
 */
@Repository
public interface ProductSnapshotRepository extends JpaRepository<ProductSnapshot, UUID> {

    /** Find a single snapshot by its Shopify product ID (GID). */
    Optional<ProductSnapshot> findByExternalId(String externalId);

    /** Quick existence check used for upsert decisions. */
    boolean existsByExternalId(String externalId);

    /** All available products, newest cache first. Used for the catalog listing. */
    List<ProductSnapshot> findByAvailableTrueOrderByUpdatedAtDesc();

    /** Snapshots refreshed since the given cutoff — used to decide whether the cache is still fresh. */
    List<ProductSnapshot> findByUpdatedAtAfter(LocalDateTime since);
}
