package com.ecommerce.orderservice.domain;

import java.time.Instant;

/**
 * Marker for domain events raised by this service's aggregates —
 * specs/iter2/3-domain-models-order-service-ddd.md §2.5.
 *
 * <p>Domain events are raised inside the aggregate and collected on it; the command service drains
 * them after persisting. The subset that crosses service boundaries is translated 1:1 into a Kafka
 * integration event by the command service (6-cqrs-command-query.md §8): `OrderPlaced` →
 * `order.created`, `OrderCancelled` → `order.cancelled`. `OrderPaid` / `OrderPaymentFailed` are
 * internal only.
 */
public interface DomainEvent {

    Instant occurredAt();
}
