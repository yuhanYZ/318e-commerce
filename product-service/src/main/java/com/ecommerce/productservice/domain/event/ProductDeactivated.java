package com.ecommerce.productservice.domain.event;

import java.time.Instant;

/** Domain event {@code ProductDeactivated} - domain-models §2.5.3, raised by P4 deactivate. */
public record ProductDeactivated(String productId, Instant occurredAt) {

    public static ProductDeactivated of(String productId) {
        return new ProductDeactivated(productId, Instant.now());
    }
}
