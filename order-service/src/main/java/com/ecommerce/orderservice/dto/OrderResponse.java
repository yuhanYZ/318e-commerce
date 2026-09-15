package com.ecommerce.orderservice.dto;

import java.time.Instant;
import java.util.List;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/OrderResponse. */
public record OrderResponse(String id, String userId, String status, MoneyDto totalAmount,
                             List<OrderItemResponse> items, Instant createdAt) {
}
