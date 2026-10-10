package com.ecommerce.orderservice.domain;

import jakarta.persistence.AttributeConverter;

/** Value Object — specs/iter2/3-domain-models-order-service-ddd.md §2.2.2. Cross-service ID only. */
public record UserId(String value) {

    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    public static class JpaConverter implements AttributeConverter<UserId, String> {

        @Override
        public String convertToDatabaseColumn(UserId attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public UserId convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new UserId(dbData);
        }
    }
}
