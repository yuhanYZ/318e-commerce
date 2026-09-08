package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.external.ProductInfo;
import com.ecommerce.orderservice.exception.ProductNotFoundException;
import com.ecommerce.orderservice.exception.ProductServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Inter-service REST client for O1 (place an order): calls Product Service's
 * GET /products/{id} (specs/openapi-contract.yaml) to snapshot the current unit
 * price for {@code OrderItem.unitPrice} (specs/3-domain-models.md section 4.2).
 * specs/2-user-stories.md's O1 scenario only calls out the Inventory Service
 * stock check explicitly, but placing an order still needs a price to compute
 * {@code totalAmount} - this call fills that gap (see the project chat summary
 * for this design decision). Unlike InventoryClient, a missing price cannot be
 * safely defaulted (silently charging $0 or a stale price is worse than
 * failing the request), so an unreachable Product Service fails the order with
 * 503 rather than degrading gracefully.
 */
@Component
@Slf4j
public class ProductClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ProductClient(
            RestTemplateBuilder builder,
            @Value("${app.services.product-service.base-url}") String baseUrl) {
        this.restTemplate = builder.build();
        this.baseUrl = baseUrl;
    }

    public ProductInfo getProduct(String productId) {
        try {
            ProductInfo response = restTemplate.getForObject(
                    baseUrl + "/products/{id}", ProductInfo.class, productId);
            if (response == null) {
                throw new ProductServiceUnavailableException(
                        "Product Service returned an empty response for productId=" + productId);
            }
            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException("Product not found: " + productId);
        } catch (RestClientException ex) {
            log.error("Product Service unavailable while pricing productId={}: {}", productId, ex.getMessage());
            throw new ProductServiceUnavailableException(
                    "Could not reach Product Service to price productId=" + productId, ex);
        }
    }
}
