package com.ecommerce.inventoryservice.domain;

/**
 * {@code Quantity} value object - domain-models §2.2.3. An immutable, non-negative stock count.
 *
 * <p>Construction rejects a negative value outright, so a negative stock level cannot exist in
 * the model. Aggregate behaviours check the arithmetic first and raise
 * {@link StockInvariantViolationException} with a business message; this constructor is the
 * backstop.
 */
public record Quantity(int value) {

    public static final Quantity ZERO = new Quantity(0);

    public Quantity {
        if (value < 0) {
            throw new IllegalArgumentException("quantity must not be negative: " + value);
        }
    }

    public boolean isZero() {
        return value == 0;
    }
}
