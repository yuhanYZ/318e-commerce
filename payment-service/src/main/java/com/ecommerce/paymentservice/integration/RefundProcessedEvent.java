package com.ecommerce.paymentservice.integration;

import java.time.Instant;

/**
 * Published to Kafka topic {@code payment.refunded} on PAY3 completion. No consumer in this
 * iteration (specs/iter2/6-cqrs-command-query.md §8 — "(none in this iteration)"); published
 * anyway for completeness/future use and so the topic exists for a demo to inspect via kafka-ui.
 */
public record RefundProcessedEvent(String refundId, String paymentId, String orderId, Instant occurredAt) {
}
