package com.ecommerce.productservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * {@code Money} value object - domain-models §2.2.3.
 *
 * <p>Immutable and self-validating: enforces ProductAggregate invariant 1
 * ({@code price.amount >= 0}) at construction, so a negative price cannot exist
 * in the model regardless of which code path builds it.
 *
 * <p>Mapped as an {@code @Embeddable} so the aggregate owns its price columns
 * rather than delegating pricing to a separate table.
 */
@Embeddable
public class Money {

    /** ISO 4217 code used when a request omits the currency (openapi Money.currency default). */
    public static final String DEFAULT_CURRENCY = "AUD";

    @Column(name = "price_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String currency;

    protected Money() {
        // JPA
    }

    public Money(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("price.amount is required");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("price.amount must not be negative");
        }
        String resolved = (currency == null || currency.isBlank()) ? DEFAULT_CURRENCY : currency.trim().toUpperCase();
        if (resolved.length() != 3) {
            throw new IllegalArgumentException("price.currency must be a 3-letter ISO 4217 code");
        }
        this.amount = amount;
        this.currency = resolved;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
