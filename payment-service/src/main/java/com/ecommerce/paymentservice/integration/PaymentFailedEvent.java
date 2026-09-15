package com.ecommerce.paymentservice.integration;

import java.time.Instant;

/**
 * Published to Kafka topic {@code payment.failed} on PAY1 mock-gateway failure — consumed by
 * Order Service's O5 (UpdateOrderOnPaymentEventCommand) and Inventory Service's I5
 * (ReleaseStockOnOrderCancelledCommand). specs/iter2/6-cqrs-command-query.md §8.
 */
public record PaymentFailedEvent(String paymentId, String orderId, Instant occurredAt) {
}
