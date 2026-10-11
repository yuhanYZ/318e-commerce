package com.ecommerce.paymentservice.domain;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Value Object — specs/iter2/3-domain-models-payment-service-ddd.md §2.2.3.
 * Non-negative amount plus an ISO 4217 currency code.
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

    /** Full refunds only: a refund amount must equal the original payment amount. */
    public boolean sameAmountAs(Money other) {
        return other != null && this.amount.compareTo(other.amount) == 0;
    }
}
