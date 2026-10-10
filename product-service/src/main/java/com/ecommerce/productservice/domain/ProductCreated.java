package com.ecommerce.productservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-product-service-ddd.md §2.5.1. */
public record ProductCreated(ProductId productId, CategoryId categoryId, Instant occurredAt)
        implements DomainEvent {
}
