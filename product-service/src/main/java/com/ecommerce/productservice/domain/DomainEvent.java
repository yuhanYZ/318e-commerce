package com.ecommerce.productservice.domain;

import java.time.Instant;

/**
 * Marker for domain events raised by this service's aggregates —
 * specs/iter2/3-domain-models-product-service-ddd.md §2.5.
 *
 * <p>Domain events are internal to the service (6-cqrs-command-query.md §8): an aggregate collects
 * them while a command runs and {@code service.DomainEventPublisher} dispatches them once the
 * aggregate has been persisted. Product Service publishes no Kafka topics.
 */
public interface DomainEvent {

    Instant occurredAt();
}
