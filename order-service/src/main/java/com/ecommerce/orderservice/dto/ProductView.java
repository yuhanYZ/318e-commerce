package com.ecommerce.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal view of Product Service's ProductDetailResponse (GET /products/{id}) — only the fields
 * O1 needs (existence, ACTIVE status, price snapshot). ignoreUnknown because the real response
 * carries several more fields (name/description/imageUrl/categoryId/stockAvailable/isInStock).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductView(String id, MoneyDto price, String status) {
}
