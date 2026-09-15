package com.ecommerce.productservice.domain.event;

import java.time.Instant;

/** Domain event {@code ProductUpdated} - domain-models §2.5.2, raised by P4 update. */
public record ProductUpdated(String productId, Instant occurredAt) {

    public static ProductUpdated of(String productId) {
        return new ProductUpdated(productId, Instant.now());
    }
}
