package com.ecommerce.paymentservice.domain;

import java.time.Instant;

/** Domain Event — §2.5.3. Published as `payment.failed`. */
public record PaymentFailed(PaymentId paymentId, OrderId orderId, Instant occurredAt) implements DomainEvent {
}
