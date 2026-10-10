package com.ecommerce.userservice.domain;

import jakarta.persistence.AttributeConverter;

/**
 * Value Object — specs/iter2/3-domain-models-user-service-ddd.md §2.2.3.
 * Constructed only from the output of a hashing function; the plaintext never reaches this type.
 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "[redacted]";
    }

    public static class JpaConverter implements AttributeConverter<PasswordHash, String> {

        @Override
        public String convertToDatabaseColumn(PasswordHash attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public PasswordHash convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new PasswordHash(dbData);
        }
    }
}
