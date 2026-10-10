package com.ecommerce.userservice.domain;

import jakarta.persistence.AttributeConverter;

import java.util.regex.Pattern;

/**
 * Value Object — specs/iter2/3-domain-models-user-service-ddd.md §2.2.2.
 * Uniqueness itself is an invariant of the {@code UserAggregate} boundary (enforced by the
 * repository lookup in the command service); this VO only guarantees a well-formed address.
 */
public record Email(String value) {

    /**
     * Deliberately no stricter than the contract's own {@code @Email} rule (which does not require
     * a dot in the domain part), so a value that passes DTO validation can never fail here and turn
     * a 400 into a 500.
     */
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public Email {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("not a valid email address");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    public static class JpaConverter implements AttributeConverter<Email, String> {

        @Override
        public String convertToDatabaseColumn(Email attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public Email convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new Email(dbData);
        }
    }
}
