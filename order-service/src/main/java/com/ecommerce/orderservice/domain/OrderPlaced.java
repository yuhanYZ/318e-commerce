package com.ecommerce.orderservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-order-service-ddd.md §2.5.1. Published as `order.created`. */
public record OrderPlaced(OrderId orderId, UserId userId, Money totalAmount, Instant occurredAt)
        implements DomainEvent {
}
