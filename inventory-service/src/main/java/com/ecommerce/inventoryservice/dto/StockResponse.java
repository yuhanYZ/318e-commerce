package com.ecommerce.inventoryservice.dto;

/**
 * OpenAPI {@code StockResponse}. {@code version} is the aggregateVersion of the StockItem stream
 * (5-event-sourcing.md §3.1). {@code isAvailable} is true when quantityAvailable is above zero.
 */
public record StockResponse(String productId, int quantityAvailable, boolean isAvailable, long version) {
}
