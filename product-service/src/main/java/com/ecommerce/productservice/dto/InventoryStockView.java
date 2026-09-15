package com.ecommerce.productservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal view of Inventory Service's StockResponse (GET /inventory/{productId}) — only the two
 * fields P2 needs. {@code ignoreUnknown} because the real response also carries productId/version
 * which Jackson would otherwise reject under Spring Boot's default fail-on-unknown-properties.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryStockView(int quantityAvailable, boolean isAvailable) {
}
