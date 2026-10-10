package com.ecommerce.aicustomerservice.domain;

import java.time.Instant;

/**
 * Marker for domain events raised by this service's aggregates —
 * specs/iter2/3-domain-models-ai-customer-service-ddd.md §2.5.
 *
 * <p>Domain events are internal to the service (6-cqrs-command-query.md §8 — AI-Customer Service
 * has no entries in the Kafka integration event catalog), so {@code service.DomainEventPublisher}
 * records them and nothing else consumes them in this iteration.
 */
public interface DomainEvent {

    Instant occurredAt();
}
