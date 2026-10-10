package com.ecommerce.orderservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-order-service-ddd.md §2.5.2. Internal only (no topic). */
public record OrderPaid(OrderId orderId, Instant occurredAt) implements DomainEvent {
}
