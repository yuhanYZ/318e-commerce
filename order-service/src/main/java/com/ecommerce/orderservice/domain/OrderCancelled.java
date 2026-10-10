package com.ecommerce.orderservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-order-service-ddd.md §2.5.4. Published as `order.cancelled`. */
public record OrderCancelled(OrderId orderId, Instant occurredAt) implements DomainEvent {
}
