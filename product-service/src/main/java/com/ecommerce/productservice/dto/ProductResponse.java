package com.ecommerce.productservice.dto;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/ProductResponse. */
public record ProductResponse(String id, String name, String description, MoneyDto price, String imageUrl,
                               String categoryId, String status) {
}
