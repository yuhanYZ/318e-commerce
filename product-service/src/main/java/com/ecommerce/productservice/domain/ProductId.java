package com.ecommerce.productservice.domain;

import jakarta.persistence.Embeddable;

/**
 * Value Object — specs/iter2/3-domain-models-product-service-ddd.md §2.2.1.
 *
 * <p>Mapped as an {@code @Embeddable}: JPA does not allow an {@code AttributeConverter} on an
 * identifier attribute, so the typed id is persisted as a single-column embeddable.
 */
@Embeddable
public record ProductId(String value) {

    public ProductId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
