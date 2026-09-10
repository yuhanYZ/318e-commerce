package com.ecommerce.orderservice.integration;

import java.time.Instant;
import java.util.List;

/**
 * Published to Kafka topic {@code order.cancelled} on O4 (cancel order) — consumed by Inventory
 * Service's I5 (ReleaseStockOnOrderCancelledCommand). specs/iter2/6-cqrs-command-query.md §8.
 */
public record OrderCancelledEvent(String orderId, List<OrderItemPayload> items, Instant occurredAt) {
}
