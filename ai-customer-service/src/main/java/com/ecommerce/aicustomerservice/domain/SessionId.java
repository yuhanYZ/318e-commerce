package com.ecommerce.aicustomerservice.domain;

import jakarta.persistence.Embeddable;

/**
 * Value Object — specs/iter2/3-domain-models-ai-customer-service-ddd.md §2.2.1.
 *
 * <p>Mapped as an {@code @Embeddable} because JPA forbids attribute converters on identifiers.
 * The value is the client-generated session identifier (see the user-stories doc's Session
 * Lifecycle note); its UUID syntax is validated by the command service, which answers a
 * syntactically invalid id with 404 rather than 400.
 */
@Embeddable
public record SessionId(String value) {

    public SessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
