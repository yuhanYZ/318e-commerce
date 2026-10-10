package com.ecommerce.orderservice.domain;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Value Object — specs/iter2/3-domain-models-order-service-ddd.md §2.2.4. Non-negative amount plus
 * an ISO 4217 currency. Amounts of different currencies are never converted in this iteration: the
 * currency of the left-hand operand is kept.
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

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /** Line-item total: unit price × quantity. */
    public Money multiply(Quantity quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity.value())), this.currency);
    }
}
