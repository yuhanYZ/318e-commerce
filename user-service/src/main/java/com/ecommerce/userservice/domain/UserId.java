package com.ecommerce.userservice.domain;

import jakarta.persistence.Embeddable;

/**
 * Value Object — specs/iter2/3-domain-models-user-service-ddd.md §2.2.1.
 *
 * <p>Mapped as an {@code @Embeddable} rather than through an attribute converter: JPA (and
 * Hibernate) do not allow an {@code AttributeConverter} on an identifier attribute, so the typed
 * id is persisted as an embeddable with a single {@code value} column.
 */
@Embeddable
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
}
