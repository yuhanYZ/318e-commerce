package com.ecommerce.paymentservice.domain;

import java.time.Instant;

/**
 * Domain Event — §2.5.6. Reserved for a future real-gateway integration: with the mock gateway a
 * requested refund is always processed, so this is never raised in this iteration.
 */
public record RefundRejected(String refundId, PaymentId paymentId, Instant occurredAt) implements DomainEvent {
}
