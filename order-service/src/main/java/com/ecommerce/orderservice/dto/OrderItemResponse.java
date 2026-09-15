package com.ecommerce.orderservice.dto;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/OrderItemResponse. */
public record OrderItemResponse(String productId, int quantity, MoneyDto unitPrice) {
}
