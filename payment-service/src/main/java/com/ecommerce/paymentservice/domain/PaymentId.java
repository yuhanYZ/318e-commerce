package com.ecommerce.paymentservice.domain;

import jakarta.persistence.Embeddable;

/**
 * Value Object — specs/iter2/3-domain-models-payment-service-ddd.md §2.2.1.
 * Mapped as an {@code @Embeddable}: JPA forbids attribute converters on identifiers.
 */
@Embeddable
public record PaymentId(String value) {

    public PaymentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("paymentId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
