package com.ecommerce.orderservice.domain;

import jakarta.persistence.AttributeConverter;

/** Value Object — specs/iter2/3-domain-models-order-service-ddd.md §2.2.5. Positive quantity. */
public record Quantity(int value) {

    public Quantity {
        if (value < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
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
