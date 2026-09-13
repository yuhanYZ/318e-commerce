package com.ecommerce.inventoryservice.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/** Kafka {@code OrderCreatedEvent} on topic order.created - 6-cqrs-command-query.md §8. Consumed by I2. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(String orderId, String userId, List<OrderItemPayload> items, Instant occurredAt) {
}
