package com.ecommerce.productservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Inventory Service's {@code StockResponse}, as consumed by P2 over REST.
 * Only the two fields P2 needs are bound; anything else Inventory adds is ignored so a
 * contract addition on their side cannot break product details.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryStockView(String productId, Integer quantityAvailable, Boolean isAvailable) {
}
