package com.ecommerce.paymentservice.domain;

import java.time.Instant;

/** Domain Event — §2.5.2. Published as `payment.confirmed`. */
public record PaymentSucceeded(PaymentId paymentId, OrderId orderId, Instant occurredAt) implements DomainEvent {
}
