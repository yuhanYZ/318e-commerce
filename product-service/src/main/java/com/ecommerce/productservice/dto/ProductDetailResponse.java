package com.ecommerce.productservice.dto;

/**
 * OpenAPI {@code ProductDetailResponse} - ProductResponse plus live stock from Inventory
 * Service (P2). Declared as allOf in the contract; flattened here because Java records
 * cannot extend another record.
 */
public record ProductDetailResponse(
        String id,
        String name,
        String description,
        MoneyDto price,
        String imageUrl,
        String categoryId,
        String status,
        int stockAvailable,
        boolean isInStock) {
}
