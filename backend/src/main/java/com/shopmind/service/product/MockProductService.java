package com.shopmind.service.product;

import com.shopmind.dto.AiDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mock product data service providing realistic shoe products for demo.
 * In production, this is replaced by ShopifyProductService.
 */
@Slf4j
@Service
public class MockProductService {

    private static final List<AiDtos.ProductData> PRODUCTS = List.of(
        AiDtos.ProductData.builder()
            .id("prod-001").title("Nike Air Zoom Pegasus 41").brand("Nike")
            .description("A versatile daily trainer with responsive ZoomX foam cushioning, breathable mesh upper, and reliable traction for mixed surfaces.")
            .productType("Running").minPrice(130.0).maxPrice(130.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600"))
            .tags(List.of("running", "daily-trainer", "cushioned", "breathable", "versatile"))
            .sizes(List.of("7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12"))
            .attributes(Map.of("cushioning", "high", "weight", "280g", "drop", "10mm", "support", "neutral", "terrain", "road"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-002").title("Adidas Ultraboost Light").brand("Adidas")
            .description("Premium all-day comfort shoe with BOOST midsole technology, Primeknit+ upper, and Continental rubber outsole for exceptional grip.")
            .productType("Lifestyle/Running").minPrice(190.0).maxPrice(190.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1608231387042-66d1773070a5?w=600"))
            .tags(List.of("running", "lifestyle", "all-day", "premium-cushioning", "stylish"))
            .sizes(List.of("7", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "13"))
            .attributes(Map.of("cushioning", "ultra-high", "weight", "305g", "drop", "10mm", "support", "neutral", "terrain", "road"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-003").title("New Balance Fresh Foam X 1080v14").brand("New Balance")
            .description("Maximum cushioning daily shoe with Fresh Foam X midsole, Hypoknit upper for targeted stretch and support, and ultra-plush comfort.")
            .productType("Running").minPrice(160.0).maxPrice(160.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1539185441755-769473a23570?w=600"))
            .tags(List.of("running", "max-cushion", "comfortable", "wide-available", "daily"))
            .sizes(List.of("7", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "13", "14"))
            .attributes(Map.of("cushioning", "maximum", "weight", "300g", "drop", "6mm", "support", "neutral", "terrain", "road", "width", "wide-available"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-004").title("Brooks Ghost 16").brand("Brooks")
            .description("Smooth daily trainer with DNA LOFT v2 cushioning, engineered mesh upper, and segmented crash pad for versatile transitions.")
            .productType("Running").minPrice(140.0).maxPrice(140.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=600"))
            .tags(List.of("running", "daily-trainer", "smooth", "neutral", "comfortable", "gym"))
            .sizes(List.of("7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "13"))
            .attributes(Map.of("cushioning", "high", "weight", "285g", "drop", "12mm", "support", "neutral", "terrain", "road"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-005").title("ASICS Gel-Nimbus 26").brand("ASICS")
            .description("Premium long-distance shoe with FF BLAST PLUS ECO cushioning, PureGEL technology, and 4D Guidance System for stability.")
            .productType("Running").minPrice(160.0).maxPrice(160.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?w=600"))
            .tags(List.of("running", "long-distance", "stability", "premium-cushioning", "comfortable"))
            .sizes(List.of("7", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12"))
            .attributes(Map.of("cushioning", "ultra-high", "weight", "310g", "drop", "8mm", "support", "mild-stability", "terrain", "road"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-006").title("Nike Air Force 1 '07").brand("Nike")
            .description("Iconic streetwear classic with Air cushioning, durable leather upper, and timeless design that pairs with everything.")
            .productType("Casual/Sneaker").minPrice(110.0).maxPrice(110.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1600269452121-4f2416e55c28?w=600"))
            .tags(List.of("casual", "sneaker", "classic", "style", "durable", "college"))
            .sizes(List.of("7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "13"))
            .attributes(Map.of("cushioning", "moderate", "weight", "380g", "drop", "flat", "support", "neutral", "terrain", "urban"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-007").title("Puma RS-X Reinvention").brand("Puma")
            .description("Retro-futuristic sneaker with RS cushioning technology, bold colorway, chunky silhouette, and all-day comfort.")
            .productType("Casual/Sneaker").minPrice(85.0).maxPrice(85.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1551107696-a4b0c5a0d9a2?w=600"))
            .tags(List.of("casual", "sneaker", "budget", "retro", "comfortable", "college", "gym-light"))
            .sizes(List.of("7", "8", "9", "9.5", "10", "10.5", "11", "12"))
            .attributes(Map.of("cushioning", "moderate", "weight", "340g", "drop", "flat", "support", "neutral", "terrain", "urban"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-008").title("Skechers Go Walk 7").brand("Skechers")
            .description("Ultra-lightweight walking shoe with Hyper Burst cushioning, Air-Cooled Goga Mat insole, and machine washable upper.")
            .productType("Walking").minPrice(75.0).maxPrice(80.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1560769629-975ec94e6a86?w=600"))
            .tags(List.of("walking", "lightweight", "comfort", "budget", "all-day", "washable"))
            .sizes(List.of("7", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "13"))
            .attributes(Map.of("cushioning", "high", "weight", "200g", "drop", "flat", "support", "neutral", "terrain", "urban"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-009").title("Nike React Infinity Run 4").brand("Nike")
            .description("Stability-focused runner with wide base, React foam, Flyknit upper, and rocker geometry to reduce injury risk.")
            .productType("Running").minPrice(160.0).maxPrice(160.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1584735175315-9d5df23860e6?w=600"))
            .tags(List.of("running", "stability", "injury-prevention", "gym", "daily", "cushioned"))
            .sizes(List.of("7", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12"))
            .attributes(Map.of("cushioning", "high", "weight", "295g", "drop", "9mm", "support", "stability", "terrain", "road"))
            .build(),

        AiDtos.ProductData.builder()
            .id("prod-010").title("Allbirds Tree Runners").brand("Allbirds")
            .description("Sustainable everyday sneaker with eucalyptus tree fiber upper, merino wool insole, and SweetFoam midsole from sugarcane.")
            .productType("Casual/Lifestyle").minPrice(98.0).maxPrice(98.0).currency("USD").available(true)
            .images(List.of("https://images.unsplash.com/photo-1525966222134-fcfa99b8ae77?w=600"))
            .tags(List.of("casual", "sustainable", "comfortable", "lightweight", "college", "daily", "minimalist"))
            .sizes(List.of("7", "8", "9", "10", "11", "12", "13"))
            .attributes(Map.of("cushioning", "moderate", "weight", "220g", "drop", "flat", "support", "neutral", "terrain", "urban", "sustainable", "true"))
            .build()
    );

    public List<AiDtos.ProductData> getAllProducts() {
        return PRODUCTS;
    }

    public List<AiDtos.ProductData> searchProducts(String query, Double maxPrice, List<String> tags) {
        return PRODUCTS.stream()
            .filter(p -> {
                if (maxPrice != null && p.getMinPrice() > maxPrice) return false;
                if (tags != null && !tags.isEmpty()) {
                    return p.getTags().stream().anyMatch(tags::contains);
                }
                if (query != null && !query.isBlank()) {
                    String q = query.toLowerCase();
                    return p.getTitle().toLowerCase().contains(q)
                        || p.getBrand().toLowerCase().contains(q)
                        || p.getDescription().toLowerCase().contains(q)
                        || p.getTags().stream().anyMatch(t -> t.contains(q));
                }
                return true;
            })
            .toList();
    }

    public Optional<AiDtos.ProductData> getProduct(String productId) {
        return PRODUCTS.stream().filter(p -> p.getId().equals(productId)).findFirst();
    }

    /**
     * Generate mock merchant offers for a product.
     */
    public List<AiDtos.MerchantInfo> getMerchantOffers(String productId) {
        var product = getProduct(productId);
        if (product.isEmpty()) return List.of();

        double basePrice = product.get().getMinPrice();
        return List.of(
            AiDtos.MerchantInfo.builder()
                .name("Shopify Direct").price(basePrice).delivery("2-3 Days")
                .returnPolicy("30d Free Returns").shipping(0.0).inStock(true)
                .url("https://shop.example.com/" + productId).bestValue(true)
                .reason("Best overall value with free shipping and easy returns")
                .build(),
            AiDtos.MerchantInfo.builder()
                .name("Amazon").price(basePrice + 5).delivery("1-2 Days")
                .returnPolicy("30d Returns").shipping(0.0).inStock(true)
                .url("https://amazon.com/dp/" + productId).bestValue(false)
                .reason("Fastest delivery option with Prime")
                .build(),
            AiDtos.MerchantInfo.builder()
                .name("Foot Locker").price(basePrice + 10).delivery("4-6 Days")
                .returnPolicy("45d Returns").shipping(8.99).inStock(true)
                .url("https://footlocker.com/" + productId).bestValue(false)
                .reason("Longest return window if you're unsure about sizing")
                .build()
        );
    }
}
