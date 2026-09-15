package com.ecommerce.paymentservice.integration;

import java.time.Instant;

/**
 * Published to Kafka topic {@code payment.confirmed} on PAY1 success — consumed by Order
 * Service's O5 (UpdateOrderOnPaymentEventCommand). specs/iter2/6-cqrs-command-query.md §8.
 */
public record PaymentConfirmedEvent(String paymentId, String orderId, Instant occurredAt) {
}
