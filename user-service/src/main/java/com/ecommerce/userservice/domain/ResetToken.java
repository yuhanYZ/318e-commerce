package com.ecommerce.userservice.domain;

import jakarta.persistence.AttributeConverter;

/** Value Object — specs/iter2/3-domain-models-user-service-ddd.md §2.2.7. */
public record ResetToken(String value) {

    public ResetToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reset token must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    public static class JpaConverter implements AttributeConverter<ResetToken, String> {

        @Override
        public String convertToDatabaseColumn(ResetToken attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public ResetToken convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new ResetToken(dbData);
        }
    }
}
