package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.AttributeConverter;

/**
 * Value Object — specs/iter2/3-domain-models-inventory-service-ddd.md §2.2.3. A stock count, which
 * is never negative (invariant 1).
 *
 * <p>{@link #plus}/{@link #minus} therefore throw if the arithmetic would land below zero. The write
 * path checks the same thing first and reports it as the domain's own 409
 * {@code StockInvariantViolation}, so these are a backstop against a corrupt event stream rather
 * than the primary rule.
 */
public record Quantity(int value) {

    public Quantity {
        if (value < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
    }

    public Quantity plus(int delta) {
        return new Quantity(value + delta);
    }

    public Quantity minus(int amount) {
        return new Quantity(value - amount);
    }

    public boolean isBelow(int threshold) {
        return value < threshold;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    public static class JpaConverter implements AttributeConverter<Quantity, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Quantity attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public Quantity convertToEntityAttribute(Integer dbData) {
            return dbData == null ? null : new Quantity(dbData);
        }
    }
}
