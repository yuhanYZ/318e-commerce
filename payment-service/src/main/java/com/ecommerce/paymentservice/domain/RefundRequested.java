package com.ecommerce.paymentservice.domain;

import java.time.Instant;

/** Domain Event — §2.5.4. Internal only. */
public record RefundRequested(String refundId, PaymentId paymentId, Money amount, Instant occurredAt)
        implements DomainEvent {
}
