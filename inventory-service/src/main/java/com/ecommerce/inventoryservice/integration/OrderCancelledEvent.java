package com.ecommerce.inventoryservice.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Kafka {@code OrderCancelledEvent} on topic order.cancelled - 6-cqrs-command-query.md §8. Consumed by I5.
 * Only the product ids are used; quantities come from what was actually deducted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(String orderId, List<OrderItemPayload> items, Instant occurredAt) {
}
