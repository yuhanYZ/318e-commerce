package com.ecommerce.inventoryservice.integration;

import java.time.Instant;

/**
 * Consumed from Kafka topic {@code payment.failed} (published by Payment Service on PAY1
 * failure) — also drives I5's ReleaseStockOnOrderCancelledCommand (a failed payment means the
 * reserved stock must be released too, per 6-cqrs-command-query.md §8's PaymentFailedEvent row).
 * Unlike OrderCancelledEvent, this payload carries no item list — see
 * {@code InventoryCommandService#releaseStockForFailedPayment} for how the line items are
 * reconstructed from the event store.
 */
public record PaymentFailedEvent(String paymentId, String orderId, Instant occurredAt) {
}
