package com.ecommerce.orderservice.integration;

import java.time.Instant;

/**
 * Consumed from Kafka topic {@code payment.failed} (published by Payment Service on PAY1
 * failure) — drives O5's UpdateOrderOnPaymentEventCommand. specs/iter2/6-cqrs-command-query.md §8.
 */
public record PaymentFailedEvent(String paymentId, String orderId, Instant occurredAt) {
}
