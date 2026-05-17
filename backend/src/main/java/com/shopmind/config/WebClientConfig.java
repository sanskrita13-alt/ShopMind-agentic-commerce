package com.shopmind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configures the WebClient used to call the Shopify Storefront GraphQL API.
 * Baseline timeout is 10 seconds — Shopify typically responds in under 500ms; longer
 * delays usually mean a misconfigured store domain or token.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient shopifyWebClient(
        @Value("${shopify.store-domain:demo-shoes.myshopify.com}") String domain,
        @Value("${shopify.api-version:2026-04}") String version,
        @Value("${shopify.storefront-token:mock}") String token
    ) {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(10));

        return WebClient.builder()
            .baseUrl("https://" + domain + "/api/" + version + "/graphql.json")
            .defaultHeader("X-Shopify-Storefront-Access-Token", token)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
