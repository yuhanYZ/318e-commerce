package com.ecommerce.productservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-product-service-ddd.md §2.5.3. */
public record ProductDeactivated(ProductId productId, Instant occurredAt) implements DomainEvent {
}
