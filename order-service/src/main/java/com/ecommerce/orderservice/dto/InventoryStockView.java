package com.ecommerce.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal view of Inventory Service's StockResponse (GET /inventory/{productId}) — only the
 * field O1 needs to confirm sufficient stock. ignoreUnknown because the real response also
 * carries productId/isAvailable/version.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryStockView(int quantityAvailable) {
}
