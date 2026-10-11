package com.ecommerce.userservice.domain;

import java.time.Instant;

/**
 * Marker for domain events raised by this service's aggregates —
 * specs/iter2/3-domain-models-user-service-ddd.md §2.5.
 *
 * <p>Domain events are internal to the service (see 6-cqrs-command-query.md §8): they are raised by
 * aggregate behaviors, collected on the aggregate, and handed to {@code service.DomainEventPublisher}
 * after the aggregate is persisted. They are deliberately not the same thing as the Kafka
 * integration events, which only a subset of services publishes.
 */
public interface DomainEvent {

    Instant occurredAt();
}
