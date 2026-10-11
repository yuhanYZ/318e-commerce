package com.ecommerce.paymentservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-payment-service-ddd.md §2.5.1. Internal only. */
public record PaymentInitiated(PaymentId paymentId, OrderId orderId, Money amount, Instant occurredAt)
        implements DomainEvent {
}
