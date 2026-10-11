package com.ecommerce.paymentservice.domain;

import jakarta.persistence.AttributeConverter;

/** Value Object — specs/iter2/3-domain-models-payment-service-ddd.md §2.2.2. Cross-service ID only. */
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

    public static class JpaConverter implements AttributeConverter<OrderId, String> {

        @Override
        public String convertToDatabaseColumn(OrderId attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public OrderId convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new OrderId(dbData);
        }
    }
}
