package com.ecommerce.inventoryservice.integration;

import java.time.Instant;
import java.util.List;

/**
 * Consumed from Kafka topic {@code order.cancelled} (published by Order Service on O4) — drives
 * I5's ReleaseStockOnOrderCancelledCommand. specs/iter2/6-cqrs-command-query.md §8.
 */
public record OrderCancelledEvent(String orderId, List<OrderItemPayload> items, Instant occurredAt) {
}
