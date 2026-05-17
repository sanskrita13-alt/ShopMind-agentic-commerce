package com.shopmind.service.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.dto.AiDtos;
import com.shopmind.entity.ProductSnapshot;
import com.shopmind.repository.ProductSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fetches live products from the Shopify Storefront GraphQL API, caches results in the
 * product_snapshots table for {@code shopmind.cache.product-ttl} seconds (default 1800 = 30 min),
 * and falls back to {@link MockProductService} when:
 *   - the storefront token equals "mock" (developer environment), or
 *   - the API call fails for any reason.
 *
 * Merchant offers come directly from the cached Shopify variants — real prices, no markup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyProductService {

    private static final String PRODUCTS_QUERY = """
        {
          products(first: 50) {
            edges {
              node {
                id
                title
                vendor
                productType
                description
                availableForSale
                tags
                priceRange {
                  minVariantPrice { amount currencyCode }
                  maxVariantPrice { amount currencyCode }
                }
                images(first: 3) {
                  edges { node { url } }
                }
                variants(first: 20) {
                  edges {
                    node {
                      id
                      title
                      availableForSale
                      price { amount currencyCode }
                      selectedOptions { name value }
                    }
                  }
                }
                metafields(identifiers: [
                  { namespace: "shopmind", key: "cushioning" },
                  { namespace: "shopmind", key: "weight" },
                  { namespace: "shopmind", key: "terrain" },
                  { namespace: "shopmind", key: "support" },
                  { namespace: "shopmind", key: "drop" },
                  { namespace: "shopmind", key: "material" }
                ]) { key value }
              }
            }
          }
        }
        """;

    private final WebClient shopifyWebClient;
    private final ProductSnapshotRepository snapshotRepo;
    private final MockProductService mockProductService;
    private final ObjectMapper objectMapper;

    @Value("${shopify.store-domain:demo-shoes.myshopify.com}")
    private String storeDomain;

    @Value("${shopify.storefront-token:mock}")
    private String storefrontToken;

    @Value("${shopmind.cache.product-ttl:1800}")
    private long cacheTtlSeconds;

    /**
     * Get all products. Serves from cache when fresh, otherwise refreshes from Shopify.
     * Falls back to the mock catalog on any failure or when no real token is configured.
     */
    public List<AiDtos.ProductData> getAllProducts() {
        if (isMockToken()) {
            log.debug("Shopify token is 'mock' — returning mock products");
            return mockProductService.getAllProducts();
        }

        try {
            if (isCacheFresh()) {
                log.debug("Serving products from Shopify cache (fresh)");
                return loadFromCache();
            }

            log.info("Refreshing product cache from Shopify Storefront API");
            return refreshFromShopify();
        } catch (Exception e) {
            log.warn("Shopify product fetch failed: {} — falling back to mock", e.getMessage());
            return mockProductService.getAllProducts();
        }
    }

    /**
     * Filter products by free-text query, max price, and tag list. Mirrors {@link MockProductService#searchProducts}
     * so the conversation service can use the same call site for both implementations.
     */
    public List<AiDtos.ProductData> searchProducts(String query, Double maxPrice, List<String> tags) {
        return getAllProducts().stream()
            .filter(p -> {
                if (maxPrice != null && p.getMinPrice() != null && p.getMinPrice() > maxPrice) {
                    return false;
                }
                if (tags != null && !tags.isEmpty()) {
                    List<String> productTags = p.getTags() != null ? p.getTags() : List.of();
                    boolean hasMatch = productTags.stream().anyMatch(tags::contains);
                    if (!hasMatch) return false;
                }
                if (query != null && !query.isBlank()) {
                    String q = query.toLowerCase();
                    List<String> productTags = p.getTags() != null ? p.getTags() : List.of();
                    boolean textMatch = (p.getTitle() != null && p.getTitle().toLowerCase().contains(q))
                        || (p.getBrand() != null && p.getBrand().toLowerCase().contains(q))
                        || (p.getDescription() != null && p.getDescription().toLowerCase().contains(q))
                        || productTags.stream().anyMatch(t -> t.toLowerCase().contains(q));
                    if (!textMatch && (tags == null || tags.isEmpty())) {
                        return false;
                    }
                }
                return true;
            })
            .toList();
    }

    public Optional<AiDtos.ProductData> getProduct(String productId) {
        return getAllProducts().stream().filter(p -> productId.equals(p.getId())).findFirst();
    }

    /**
     * Returns merchant offers derived from real Shopify variant prices. The cheapest in-stock
     * variant becomes the canonical "Shopify Direct" offer. Falls back to mock merchants when
     * no live cache exists.
     */
    public List<AiDtos.MerchantInfo> getMerchantOffers(String productId) {
        if (isMockToken()) {
            return mockProductService.getMerchantOffers(productId);
        }

        var snapshot = snapshotRepo.findByExternalId(productId);
        if (snapshot.isEmpty() || snapshot.get().getVariantsJson() == null) {
            return mockProductService.getMerchantOffers(productId);
        }

        try {
            JsonNode variants = objectMapper.readTree(snapshot.get().getVariantsJson());
            double cheapest = Double.MAX_VALUE;
            boolean inStock = false;
            String checkoutUrl = "https://" + storeDomain + "/products/"
                + productId.replaceFirst(".*/", "");

            for (JsonNode v : variants) {
                boolean available = v.path("availableForSale").asBoolean(false);
                double price = v.path("price").asDouble(Double.MAX_VALUE);
                if (available && price < cheapest) {
                    cheapest = price;
                    inStock = true;
                }
            }

            if (cheapest == Double.MAX_VALUE) {
                cheapest = snapshot.get().getMinPrice() != null ? snapshot.get().getMinPrice() : 0.0;
            }

            String currency = snapshot.get().getCurrency() != null ? snapshot.get().getCurrency() : "USD";
            return List.of(AiDtos.MerchantInfo.builder()
                .name("Shopify Direct")
                .price(cheapest)
                .delivery("2-3 Days")
                .returnPolicy("30d Free Returns")
                .shipping(0.0)
                .inStock(inStock)
                .url(checkoutUrl)
                .bestValue(true)
                .reason("Live price from " + storeDomain + " with free shipping and easy returns")
                .build());
        } catch (Exception e) {
            log.warn("Failed to parse variants for {}: {}", productId, e.getMessage());
            return mockProductService.getMerchantOffers(productId);
        }
    }

    // ─── Cache + Shopify API helpers ──────────────────────────────────────────

    private boolean isMockToken() {
        return storefrontToken == null || storefrontToken.isBlank() || "mock".equalsIgnoreCase(storefrontToken);
    }

    private boolean isCacheFresh() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(cacheTtlSeconds);
        return !snapshotRepo.findByUpdatedAtAfter(cutoff).isEmpty();
    }

    private List<AiDtos.ProductData> loadFromCache() {
        return snapshotRepo.findByAvailableTrueOrderByUpdatedAtDesc().stream()
            .map(this::snapshotToProductData)
            .toList();
    }

    @Transactional
    public List<AiDtos.ProductData> refreshFromShopify() {
        Map<String, Object> body = Map.of("query", PRODUCTS_QUERY);

        JsonNode response = shopifyWebClient.post()
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block(Duration.ofSeconds(10));

        if (response == null) {
            throw new IllegalStateException("Empty response from Shopify");
        }
        if (response.has("errors") && !response.get("errors").isEmpty()) {
            throw new IllegalStateException("Shopify GraphQL errors: " + response.get("errors"));
        }

        JsonNode edges = response.path("data").path("products").path("edges");
        if (edges.isMissingNode() || !edges.isArray()) {
            throw new IllegalStateException("Unexpected Shopify response shape");
        }

        List<AiDtos.ProductData> results = new ArrayList<>();
        for (JsonNode edge : edges) {
            JsonNode node = edge.path("node");
            if (node.isMissingNode()) continue;

            AiDtos.ProductData data = nodeToProductData(node);
            results.add(data);
            upsertSnapshot(node, data);
        }
        log.info("Refreshed {} products from Shopify ({})", results.size(), storeDomain);
        return results;
    }

    private AiDtos.ProductData nodeToProductData(JsonNode node) {
        String id = node.path("id").asText("");
        String title = node.path("title").asText("");
        String vendor = node.path("vendor").asText("");
        String productType = node.path("productType").asText("");
        String description = node.path("description").asText("");
        boolean available = node.path("availableForSale").asBoolean(false);

        Double minPrice = node.path("priceRange").path("minVariantPrice").path("amount").isMissingNode()
            ? null : node.path("priceRange").path("minVariantPrice").path("amount").asDouble();
        Double maxPrice = node.path("priceRange").path("maxVariantPrice").path("amount").isMissingNode()
            ? null : node.path("priceRange").path("maxVariantPrice").path("amount").asDouble();
        String currency = node.path("priceRange").path("minVariantPrice").path("currencyCode").asText("USD");

        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = node.path("tags");
        if (tagsNode.isArray()) {
            tagsNode.forEach(t -> tags.add(t.asText()));
        }

        List<String> images = new ArrayList<>();
        for (JsonNode imgEdge : node.path("images").path("edges")) {
            String url = imgEdge.path("node").path("url").asText("");
            if (!url.isEmpty()) images.add(url);
        }

        List<String> sizes = new ArrayList<>();
        for (JsonNode varEdge : node.path("variants").path("edges")) {
            JsonNode vNode = varEdge.path("node");
            for (JsonNode opt : vNode.path("selectedOptions")) {
                if ("Size".equalsIgnoreCase(opt.path("name").asText())) {
                    String value = opt.path("value").asText("");
                    if (!value.isEmpty() && !sizes.contains(value)) sizes.add(value);
                }
            }
        }

        Map<String, Object> attributes = extractAttributes(node, tags, description);

        return AiDtos.ProductData.builder()
            .id(id)
            .title(title)
            .brand(vendor)
            .description(description)
            .productType(productType)
            .images(images)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .currency(currency)
            .available(available)
            .tags(tags)
            .sizes(sizes)
            .attributes(attributes)
            .build();
    }

    /**
     * Build the attributes map from Shopify metafields when present, otherwise infer from tags
     * and description. Each tag of the form "key:value" (e.g. "cushioning:high") becomes a map entry.
     */
    private Map<String, Object> extractAttributes(JsonNode node, List<String> tags, String description) {
        Map<String, Object> attributes = new HashMap<>();

        // Metafield-driven attributes (preferred)
        for (JsonNode mf : node.path("metafields")) {
            if (mf.isNull() || mf.isMissingNode()) continue;
            String key = mf.path("key").asText("");
            String value = mf.path("value").asText("");
            if (!key.isEmpty() && !value.isEmpty()) {
                attributes.put(key, value);
            }
        }

        // Tag inference fallback for any attribute not already set from metafields
        for (String tag : tags) {
            if (!tag.contains(":")) continue;
            String[] parts = tag.split(":", 2);
            String key = parts[0].trim();
            String value = parts[1].trim();
            attributes.putIfAbsent(key, value);
        }

        // Description heuristics for cushioning intensity when no explicit value exists
        if (!attributes.containsKey("cushioning") && description != null) {
            String d = description.toLowerCase();
            if (d.contains("ultra cushion") || d.contains("ultra-cushion")) {
                attributes.put("cushioning", "ultra-high");
            } else if (d.contains("max cushion") || d.contains("maximum cushion")) {
                attributes.put("cushioning", "maximum");
            } else if (d.contains("high cushion")) {
                attributes.put("cushioning", "high");
            }
        }

        // Default terrain to urban for casual/college tags
        if (!attributes.containsKey("terrain")) {
            boolean urban = tags.stream().anyMatch(t -> {
                String low = t.toLowerCase();
                return low.contains("casual") || low.contains("college")
                    || low.contains("gym") || low.contains("lifestyle");
            });
            if (urban) attributes.put("terrain", "urban");
        }

        return attributes;
    }

    private void upsertSnapshot(JsonNode node, AiDtos.ProductData data) {
        String externalId = data.getId();
        if (externalId == null || externalId.isEmpty()) return;

        ProductSnapshot snapshot = snapshotRepo.findByExternalId(externalId)
            .orElseGet(() -> ProductSnapshot.builder()
                .externalId(externalId)
                .sourceStore(storeDomain)
                .build());

        snapshot.setTitle(data.getTitle() != null ? data.getTitle() : externalId);
        snapshot.setBrand(data.getBrand());
        snapshot.setProductType(data.getProductType());
        snapshot.setDescription(data.getDescription());
        snapshot.setMinPrice(data.getMinPrice());
        snapshot.setMaxPrice(data.getMaxPrice());
        snapshot.setCurrency(data.getCurrency());
        snapshot.setAvailable(data.getAvailable());
        snapshot.setSourceStore(storeDomain);

        try {
            snapshot.setImagesJson(objectMapper.writeValueAsString(data.getImages()));
            snapshot.setTagsJson(objectMapper.writeValueAsString(data.getTags()));
            // Persist a compact variants representation we control (id, title, price, availability)
            List<Map<String, Object>> compactVariants = new ArrayList<>();
            for (JsonNode varEdge : node.path("variants").path("edges")) {
                JsonNode vNode = varEdge.path("node");
                Map<String, Object> v = new HashMap<>();
                v.put("id", vNode.path("id").asText(""));
                v.put("title", vNode.path("title").asText(""));
                v.put("availableForSale", vNode.path("availableForSale").asBoolean(false));
                v.put("price", vNode.path("price").path("amount").asDouble(0.0));
                v.put("currency", vNode.path("price").path("currencyCode").asText("USD"));
                List<Map<String, String>> opts = new ArrayList<>();
                for (JsonNode opt : vNode.path("selectedOptions")) {
                    opts.add(Map.of(
                        "name", opt.path("name").asText(""),
                        "value", opt.path("value").asText("")
                    ));
                }
                v.put("selectedOptions", opts);
                compactVariants.add(v);
            }
            snapshot.setVariantsJson(objectMapper.writeValueAsString(compactVariants));
        } catch (Exception e) {
            log.warn("Failed to serialize snapshot fields for {}: {}", externalId, e.getMessage());
        }

        snapshotRepo.save(snapshot);
    }

    private AiDtos.ProductData snapshotToProductData(ProductSnapshot s) {
        List<String> images = parseJsonArrayOfStrings(s.getImagesJson());
        List<String> tags = parseJsonArrayOfStrings(s.getTagsJson());

        List<String> sizes = new ArrayList<>();
        Map<String, Object> attributes = new HashMap<>();

        if (s.getVariantsJson() != null) {
            try {
                JsonNode variants = objectMapper.readTree(s.getVariantsJson());
                for (JsonNode v : variants) {
                    for (JsonNode opt : v.path("selectedOptions")) {
                        if ("Size".equalsIgnoreCase(opt.path("name").asText())) {
                            String value = opt.path("value").asText("");
                            if (!value.isEmpty() && !sizes.contains(value)) sizes.add(value);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not parse variantsJson for {}: {}", s.getExternalId(), e.getMessage());
            }
        }

        // Re-derive attributes from tags (metafields aren't re-stored in snapshot)
        for (String tag : tags) {
            if (!tag.contains(":")) continue;
            String[] parts = tag.split(":", 2);
            attributes.putIfAbsent(parts[0].trim(), parts[1].trim());
        }
        if (!attributes.containsKey("terrain")) {
            boolean urban = tags.stream().anyMatch(t -> {
                String low = t.toLowerCase();
                return low.contains("casual") || low.contains("college")
                    || low.contains("gym") || low.contains("lifestyle");
            });
            if (urban) attributes.put("terrain", "urban");
        }

        return AiDtos.ProductData.builder()
            .id(s.getExternalId())
            .title(s.getTitle())
            .brand(s.getBrand())
            .description(s.getDescription())
            .productType(s.getProductType())
            .images(images)
            .minPrice(s.getMinPrice())
            .maxPrice(s.getMaxPrice())
            .currency(s.getCurrency())
            .available(s.getAvailable())
            .tags(tags)
            .sizes(sizes)
            .attributes(attributes)
            .build();
    }

    private List<String> parseJsonArrayOfStrings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
