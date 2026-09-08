package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.external.StockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Inter-service REST client for P2 (view product details): calls Inventory
 * Service's GET /inventory/{productId} (specs/openapi-contract.yaml). Inventory
 * Service does not exist yet in this repo, so any connection failure is caught
 * and treated as "no stock info available" rather than failing the whole
 * product-detail request - this lets Product Service be developed and tested
 * standalone, and it will pick up real stock data automatically once
 * inventory-service is stood up at the configured base URL.
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

    public StockResponse getStock(String productId) {
        try {
            StockResponse response = restTemplate.getForObject(
                    baseUrl + "/inventory/{productId}", StockResponse.class, productId);
            return response != null ? response : emptyStock(productId);
        } catch (RestClientException ex) {
            log.warn("Inventory Service unavailable while fetching stock for productId={}: {}",
                    productId, ex.getMessage());
            return emptyStock(productId);
        }
    }

    private StockResponse emptyStock(String productId) {
        return new StockResponse(productId, 0, false);
    }
}
