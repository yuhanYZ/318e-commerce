package com.ecommerce.productservice.domain;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Value Object — specs/iter2/3-domain-models-product-service-ddd.md §2.2.3.
 * Invariant 1 (`price.amount >= 0`) is enforced here, at construction.
 */
@Embeddable
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("money amount must be non-negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("money currency must not be blank");
        }
    }
}
