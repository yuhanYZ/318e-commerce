package com.ecommerce.productservice.dto;

import jakarta.validation.Valid;

/**
 * OpenAPI {@code ProductUpdateRequest} - partial update. Every field is optional; omitted
 * fields are left unchanged by {@code Product.update(...)}.
 */
public record ProductUpdateRequest(
        String name,
        String description,
        @Valid MoneyDto price,
        String imageUrl,
        String categoryId) {
}
