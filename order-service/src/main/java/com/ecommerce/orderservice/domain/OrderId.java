package com.ecommerce.orderservice.domain;

import jakarta.persistence.Embeddable;

/**
 * Value Object — specs/iter2/3-domain-models-order-service-ddd.md §2.2.1.
 * Mapped as an {@code @Embeddable} because JPA forbids attribute converters on identifiers.
 */
@Embeddable
public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
