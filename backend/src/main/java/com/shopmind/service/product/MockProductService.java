package com.shopmind.service.product;

import com.shopmind.dto.AiDtos;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Product catalog loaded from nike_products.csv at startup.
 * Falls back to 5 hardcoded products if the CSV cannot be parsed.
 * Used by ShopifyProductService as a fallback when Shopify API is unavailable.
 */
@Slf4j
@Service
public class MockProductService {

    private List<AiDtos.ProductData> products = new ArrayList<>();

    @PostConstruct
    public void init() {
        try {
            products = loadFromCsv();
            log.info("Loaded {} products from nike_products.csv", products.size());
        } catch (Exception e) {
            log.warn("Could not load nike_products.csv ({}), using hardcoded fallback", e.getMessage());
            products = hardcodedFallback();
        }
    }

    // ─── CSV loader ───────────────────────────────────────────────────────────

    private List<AiDtos.ProductData> loadFromCsv() throws Exception {
        ClassPathResource resource = new ClassPathResource("nike_products.csv");
        if (!resource.exists()) throw new IllegalStateException("nike_products.csv not found in classpath");

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(reader);

            // Group variant rows by URL handle — first row with non-blank Title is the product header
            Map<String, ProductBuilder> productMap = new LinkedHashMap<>();
            String currentHandle = null;

            for (CSVRecord record : records) {
                String title   = safe(record, "Title");
                String handle  = safe(record, "URLHandle");
                String sku     = safe(record, "SKU");
                String size    = safe(record, "Size");
                String priceStr = safe(record, "Price");

                // If handle is blank, use the last known handle (continuation row)
                if (!handle.isBlank()) currentHandle = handle;
                if (currentHandle == null) continue;

                ProductBuilder builder = productMap.computeIfAbsent(currentHandle, h -> new ProductBuilder(h));

                // First row fills the product-level fields
                if (!title.isBlank()) {
                    builder.title       = title;
                    builder.description = safe(record, "Description");
                    builder.type        = safe(record, "Type");
                    builder.tags        = safe(record, "Tags");
                    builder.imageUrl    = safe(record, "ImageURL");
                    try { builder.weightG = Integer.parseInt(safe(record, "WeightG")); } catch (Exception ignored) {}
                    try { builder.price   = Double.parseDouble(priceStr); } catch (Exception ignored) {}
                }

                if (!size.isBlank()) builder.sizes.add(size);
                if (!sku.isBlank())  builder.skus.add(sku);
            }

            return productMap.values().stream()
                .filter(b -> b.title != null && !b.title.isBlank())
                .map(ProductBuilder::build)
                .collect(Collectors.toList());
        }
    }

    private String safe(CSVRecord record, String col) {
        try { return record.get(col) == null ? "" : record.get(col).trim(); }
        catch (Exception e) { return ""; }
    }

    // ─── Inner builder ────────────────────────────────────────────────────────

    private static class ProductBuilder {
        final String handle;
        String title, description, type, tags, imageUrl;
        int weightG = 0;
        double price = 0;
        final List<String> sizes = new ArrayList<>();
        final List<String> skus  = new ArrayList<>();

        ProductBuilder(String handle) { this.handle = handle; }

        AiDtos.ProductData build() {
            List<String> tagList = Arrays.stream((tags == null ? "" : tags).split("[,\\s]+"))
                .map(String::toLowerCase)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());

            Map<String, Object> attrs = inferAttributes(type == null ? "" : type, tagList, weightG);

            String img = imageUrl != null && !imageUrl.isBlank() ? imageUrl
                : "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=1200&q=80";

            return AiDtos.ProductData.builder()
                .id("csv-" + handle)
                .title(title)
                .brand("Nike")
                .description(description)
                .productType(type)
                .minPrice(price)
                .maxPrice((double) Math.round(price * 1.15))
                .currency("INR")
                .available(true)
                .images(List.of(img))
                .tags(tagList)
                .sizes(sizes.isEmpty() ? List.of("UK 6","UK 7","UK 8","UK 9","UK 10") : sizes)
                .attributes(attrs)
                .build();
        }

        private static Map<String, Object> inferAttributes(String type, List<String> tags, int weightG) {
            Map<String, Object> a = new HashMap<>();

            // Cushioning from type
            String t = type.toLowerCase();
            if (t.contains("marathon") || t.contains("racing"))        a.put("cushioning", "ultra-high");
            else if (t.contains("max cushion") || t.contains("invincible") || t.contains("vomero")) a.put("cushioning", "maximum");
            else if (t.contains("running") || t.contains("trail"))     a.put("cushioning", "high");
            else if (t.contains("training"))                           a.put("cushioning", "moderate");
            else                                                        a.put("cushioning", "moderate");

            // Terrain
            if (t.contains("trail"))                                   a.put("terrain", "trail");
            else if (t.contains("football") || t.contains("boot"))     a.put("terrain", "grass");
            else if (t.contains("training"))                           a.put("terrain", "gym");
            else                                                        a.put("terrain", "urban");

            // Support
            if (tags.contains("stability") || t.contains("structure")) a.put("support", "stability");
            else                                                        a.put("support", "neutral");

            // Weight
            if (weightG > 0) a.put("weight", weightG + "g");

            return a;
        }
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    public List<AiDtos.ProductData> getAllProducts() {
        return products;
    }

    public List<AiDtos.ProductData> searchProducts(String query, Double maxPrice, List<String> tags) {
        return products.stream()
            .filter(p -> {
                if (maxPrice != null && p.getMinPrice() != null && p.getMinPrice() > maxPrice) return false;
                if (tags != null && !tags.isEmpty()) {
                    boolean match = p.getTags().stream().anyMatch(tags::contains);
                    if (!match) return false;
                }
                if (query != null && !query.isBlank()) {
                    String q = query.toLowerCase();
                    return p.getTitle().toLowerCase().contains(q)
                        || (p.getBrand() != null && p.getBrand().toLowerCase().contains(q))
                        || (p.getDescription() != null && p.getDescription().toLowerCase().contains(q))
                        || p.getTags().stream().anyMatch(t -> t.contains(q));
                }
                return true;
            })
            .toList();
    }

    public Optional<AiDtos.ProductData> getProduct(String productId) {
        return products.stream().filter(p -> productId.equals(p.getId())).findFirst();
    }

    public List<AiDtos.MerchantInfo> getMerchantOffers(String productId) {
        var product = getProduct(productId);
        if (product.isEmpty()) return List.of();

        double basePrice = product.get().getMinPrice() != null ? product.get().getMinPrice() : 9999;
        return List.of(
            AiDtos.MerchantInfo.builder()
                .name("Nike India").price(basePrice).delivery("2-3 Days")
                .returnPolicy("30-day free returns").shipping(0.0).inStock(true)
                .url("https://nike.com/in").bestValue(true)
                .reason("Official Nike India store — best price with free shipping and easy returns")
                .build(),
            AiDtos.MerchantInfo.builder()
                .name("Myntra").price((double) Math.round(basePrice * 0.95)).delivery("3-5 Days")
                .returnPolicy("30-day returns").shipping(0.0).inStock(true)
                .url("https://myntra.com").bestValue(false)
                .reason("Often has discounts — check for sale pricing")
                .build(),
            AiDtos.MerchantInfo.builder()
                .name("Amazon India").price(basePrice).delivery("1-2 Days (Prime)")
                .returnPolicy("10-day returns").shipping(0.0).inStock(true)
                .url("https://amazon.in").bestValue(false)
                .reason("Fastest delivery with Prime")
                .build()
        );
    }

    // ─── Hardcoded fallback (used only if CSV fails to load) ──────────────────

    private static List<AiDtos.ProductData> hardcodedFallback() {
        return List.of(
            AiDtos.ProductData.builder()
                .id("prod-001").title("Nike Air Zoom Pegasus 41").brand("Nike")
                .description("ReactX foam delivers 13% more energy return. Dual Zoom Air for explosive responsiveness.")
                .productType("Running Shoes").minPrice(11895.0).maxPrice(13495.0).currency("INR").available(true)
                .images(List.of("https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=1200&q=80"))
                .tags(List.of("running","daily-trainer","cushioned","breathable","versatile","pegasus"))
                .sizes(List.of("UK 6","UK 7","UK 8","UK 9","UK 10"))
                .attributes(Map.of("cushioning","high","weight","880g","terrain","road","support","neutral"))
                .build(),
            AiDtos.ProductData.builder()
                .id("prod-002").title("Nike Dunk Low Retro").brand("Nike")
                .description("Crisp overlays and original team colours from the hardwood to the streets.")
                .productType("Lifestyle Sneakers").minPrice(8995.0).maxPrice(9995.0).currency("INR").available(true)
                .images(List.of("https://images.unsplash.com/photo-1600269452121-4f2416e55c28?w=1200&q=80"))
                .tags(List.of("lifestyle","casual","streetwear","college","dunk","retro"))
                .sizes(List.of("UK 6","UK 7","UK 8","UK 9","UK 10"))
                .attributes(Map.of("cushioning","moderate","terrain","urban","support","neutral"))
                .build(),
            AiDtos.ProductData.builder()
                .id("prod-003").title("Nike Air Max 270").brand("Nike")
                .description("Tallest-ever Air unit in the heel for all-day comfort with breathable mesh upper.")
                .productType("Lifestyle Sneakers").minPrice(13995.0).maxPrice(15995.0).currency("INR").available(true)
                .images(List.of("https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=1200&q=80"))
                .tags(List.of("lifestyle","casual","air-max","comfortable","college","daily"))
                .sizes(List.of("UK 6","UK 7","UK 8","UK 9","UK 10"))
                .attributes(Map.of("cushioning","high","terrain","urban","support","neutral"))
                .build(),
            AiDtos.ProductData.builder()
                .id("prod-004").title("Nike Metcon 9").brand("Nike")
                .description("Gold standard for weight-day workouts with wider Hyperlift heel plate.")
                .productType("Training Shoes").minPrice(13995.0).maxPrice(15995.0).currency("INR").available(true)
                .images(List.of("https://images.unsplash.com/photo-1539185441755-769473a23570?w=1200&q=80"))
                .tags(List.of("training","gym","crossfit","weightlifting","metcon"))
                .sizes(List.of("UK 6","UK 7","UK 8","UK 9","UK 10"))
                .attributes(Map.of("cushioning","moderate","terrain","gym","support","stability"))
                .build(),
            AiDtos.ProductData.builder()
                .id("prod-005").title("Nike Revolution 7").brand("Nike")
                .description("Soft ride with lightweight knit upper for everyday runs and errands.")
                .productType("Running Shoes").minPrice(3795.0).maxPrice(4495.0).currency("INR").available(true)
                .images(List.of("https://images.unsplash.com/photo-1551107696-a4b0c5a0d9a2?w=1200&q=80"))
                .tags(List.of("running","budget","everyday","comfortable","lightweight"))
                .sizes(List.of("UK 6","UK 7","UK 8","UK 9","UK 10"))
                .attributes(Map.of("cushioning","moderate","terrain","road","support","neutral"))
                .build()
        );
    }
}
