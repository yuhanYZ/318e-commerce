package com.ecommerce.inventoryservice.integration;

import java.time.Instant;
import java.util.List;

/**
 * Consumed from Kafka topic {@code order.created} (published by Order Service on O1) — drives
 * I2's DeductStockOnOrderCreatedCommand. specs/iter2/6-cqrs-command-query.md §8.
 */
public record OrderCreatedEvent(String orderId, String userId, List<OrderItemPayload> items, Instant occurredAt) {
}
