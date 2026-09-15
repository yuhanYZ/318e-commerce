package com.ecommerce.productservice.dto;

/**
 * specs/iter2/openapi-contract-v2.yaml components/schemas/ProductDetailResponse (allOf
 * ProductResponse + stockAvailable/isInStock). Flattened into one record rather than modelling
 * the allOf as inheritance — simpler for a single response DTO with no reuse need.
 */
public record ProductDetailResponse(String id, String name, String description, MoneyDto price, String imageUrl,
                                     String categoryId, String status, int stockAvailable, boolean isInStock) {
}
