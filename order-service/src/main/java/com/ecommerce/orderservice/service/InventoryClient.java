package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.external.StockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Inter-service REST client for O1 (place an order): calls Inventory Service's
 * GET /inventory/{productId} (specs/openapi-contract.yaml) to confirm stock
 * before creating the order, per specs/2-user-stories.md User Story O1 step ii.
 * Inventory Service does not exist yet in this repo, so a connection failure is
 * treated as "assume sufficient stock" rather than blocking every order - this
 * mirrors product-service's InventoryClient so Order Service stays
 * independently developable/testable, and both will pick up real stock data
 * automatically once inventory-service is stood up at the configured base URL.
 * This is more permissive than product-service's read-only fallback because it
 * lets order creation succeed during development; it MUST be revisited once
 * inventory-service exists so a genuinely-down dependency can't silently oversell.
 */
@Component
@Slf4j
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InventoryClient(
            RestTemplateBuilder builder,
            @Value("${app.services.inventory-service.base-url}") String baseUrl) {
        this.restTemplate = builder.build();
        this.baseUrl = baseUrl;
    }

    /** Returns the current stock for a product, or an optimistic "available" placeholder if Inventory Service can't be reached. */
    public StockResponse getStock(String productId) {
        try {
            StockResponse response = restTemplate.getForObject(
                    baseUrl + "/inventory/{productId}", StockResponse.class, productId);
            return response != null ? response : optimisticStock(productId);
        } catch (RestClientException ex) {
            log.warn("Inventory Service unavailable while checking stock for productId={}; assuming sufficient stock: {}",
                    productId, ex.getMessage());
            return optimisticStock(productId);
        }
    }

    private StockResponse optimisticStock(String productId) {
        return new StockResponse(productId, Integer.MAX_VALUE, true);
    }
}
