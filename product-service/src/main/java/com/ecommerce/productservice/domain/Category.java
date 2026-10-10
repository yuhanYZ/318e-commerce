package com.ecommerce.productservice.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * {@code Category} entity — specs/iter2/3-domain-models-product-service-ddd.md §2.1.1.
 * Single-level only: there is deliberately no parent/child hierarchy.
 *
 * <p>Mapped and persisted so the domain class is real rather than documentation-only, but note
 * that the OpenAPI contract exposes no category write endpoint (see 4-api-endpoints-v2.md §2), so
 * nothing in this iteration creates or browses categories through the API and
 * {@code Product}'s "categoryId must reference an existing Category" invariant (invariant 3) is
 * therefore left unenforced — there is no way for a caller to satisfy a stricter rule, and the
 * contract tests create products with arbitrary category ids.
 */
@Entity
@Table(name = "category")
public class Category {

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id", nullable = false))
    private CategoryId id;

    @Column(nullable = false)
    private String name;

    protected Category() {
        // JPA
    }

    public Category(CategoryId id, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("category name must not be blank");
        }
        this.id = id;
        this.name = name;
    }

    public CategoryId getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
