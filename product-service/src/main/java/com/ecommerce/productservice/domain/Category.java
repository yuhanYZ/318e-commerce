package com.ecommerce.productservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Represents a product category used for catalog browsing (P1).
 * Maps to the {@code Category} domain class in specs/3-domain-models.md section 3.2.
 * NOTE: no CRUD endpoints are defined for categories in specs/4-api-endpoints.md -
 * this entity exists for domain-model completeness and future category-management
 * endpoints; Product#categoryId is a plain string reference, not a JPA foreign key.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    /** Nullable - multi-level nesting support is an open question per specs/3-domain-models.md section 9. */
    private String parentCategoryId;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }
}
