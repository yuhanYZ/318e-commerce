package com.ecommerce.inventoryservice.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Kafka {@code PaymentFailedEvent} on topic payment.failed - 6-cqrs-command-query.md §8. Consumed by I5.
 * Carries no items; the order lines are recovered from StockDeducted history.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(String paymentId, String orderId, Instant occurredAt) {
}
