package com.ecommerce.productservice.domain.event;

import java.time.Instant;

/**
 * Domain event {@code ProductCreated} - domain-models §2.5.1, raised by P4 create.
 *
 * <p>Published in-process via Spring's ApplicationEventPublisher. Product events have no
 * Kafka topic in this iteration: the integration-event catalog in 6-cqrs-command-query.md §8
 * covers Order and Payment events only.
 */
public record ProductCreated(String productId, String categoryId, Instant occurredAt) {

    public static ProductCreated of(String productId, String categoryId) {
        return new ProductCreated(productId, categoryId, Instant.now());
    }
}
