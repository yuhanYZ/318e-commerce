package com.ecommerce.productservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-product-service-ddd.md §2.5.2. */
public record ProductUpdated(ProductId productId, Instant occurredAt) implements DomainEvent {
}
