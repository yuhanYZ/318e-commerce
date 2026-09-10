package com.ecommerce.orderservice.integration;

import java.time.Instant;
import java.util.List;

/**
 * Published to Kafka topic {@code order.created} on O1 (place order) — consumed by Inventory
 * Service's I2 (DeductStockOnOrderCreatedCommand). specs/iter2/6-cqrs-command-query.md §8.
 */
public record OrderCreatedEvent(String orderId, String userId, List<OrderItemPayload> items, Instant occurredAt) {
}
