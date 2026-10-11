package com.ecommerce.paymentservice.domain;

import java.time.Instant;

/**
 * Marker for domain events raised by this service's aggregates —
 * specs/iter2/3-domain-models-payment-service-ddd.md §2.5.
 *
 * <p>The subset that crosses service boundaries is translated 1:1 into a Kafka integration event
 * by the command service (6-cqrs-command-query.md §8): `PaymentSucceeded` → `payment.confirmed`,
 * `PaymentFailed` → `payment.failed`, `RefundProcessed` → `payment.refunded`.
 * `PaymentInitiated`, `RefundRequested` and `RefundRejected` are internal only.
 */
public interface DomainEvent {

    Instant occurredAt();
}
