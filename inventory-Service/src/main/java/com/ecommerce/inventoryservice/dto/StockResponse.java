package com.ecommerce.inventoryservice.dto;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/StockResponse. */
public record StockResponse(String productId, int quantityAvailable, boolean isAvailable, long version) {
}
