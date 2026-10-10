package com.ecommerce.orderservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-order-service-ddd.md §2.5.3. Internal only (no topic). */
public record OrderPaymentFailed(OrderId orderId, Instant occurredAt) implements DomainEvent {
}
