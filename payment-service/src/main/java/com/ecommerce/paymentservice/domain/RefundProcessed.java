package com.ecommerce.paymentservice.domain;

import java.time.Instant;

/** Domain Event — §2.5.5. Published as `payment.refunded`. */
public record RefundProcessed(String refundId, PaymentId paymentId, Instant occurredAt) implements DomainEvent {
}
