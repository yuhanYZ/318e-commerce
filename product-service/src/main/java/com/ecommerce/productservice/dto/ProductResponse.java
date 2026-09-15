package com.ecommerce.productservice.dto;

/** OpenAPI {@code ProductResponse}. */
public record ProductResponse(
        String id,
        String name,
        String description,
        MoneyDto price,
        String imageUrl,
        String categoryId,
        String status) {
}
