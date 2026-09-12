package com.ecommerce.productservice.domain;

import com.ecommerce.productservice.domain.event.ProductCreated;
import com.ecommerce.productservice.domain.event.ProductDeactivated;
import com.ecommerce.productservice.domain.event.ProductUpdated;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code ProductAggregate} (root: Product) - domain-models §2.3.1.
 *
 * <p>Business rules live here, not in the application services, per the layering rule in
 * 1-technical-architecture-v2.md: the domain layer holds invariants, the service layer only
 * orchestrates. State is mutated exclusively through {@link #create}, {@link #update} and
 * {@link #deactivate}; there are no public setters.
 *
 * <p>Invariants:
 * <ol>
 *   <li>{@code price.amount >= 0} - enforced by {@link Money} at construction.</li>
 *   <li>{@code INACTIVE} is terminal; {@link #update} on an inactive product is rejected
 *       ({@link InactiveProductException}, surfaced as HTTP 409 ProductStateViolation).</li>
 *   <li>{@code categoryId} must reference an existing Category. <b>Not enforced in this
 *       iteration</b>: the OpenAPI contract defines no endpoint to create or list categories,
 *       so there is nothing to validate against. Deliberate simplification, not an oversight.</li>
 * </ol>
 *
 * <p>Stock is intentionally absent: it belongs to Inventory Service's StockItem and is
 * fetched over REST for P2.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Embedded
    private Money price;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Events raised but not yet published; drained by the application service after save. */
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    protected Product() {
        // JPA
    }

    private Product(String id, String name, String description, Money price, String imageUrl, String categoryId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.categoryId = categoryId;
        this.status = ProductStatus.ACTIVE;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Behaviour {@code create(...)} - domain-models §2.3.1. Emits {@link ProductCreated}.
     * New products always start {@link ProductStatus#ACTIVE}.
     */
    public static Product create(String id, String name, String description, Money price,
                                 String imageUrl, String categoryId) {
        requireText(name, "name is required");
        requireText(categoryId, "categoryId is required");
        if (price == null) {
            throw new IllegalArgumentException("price is required");
        }
        Product product = new Product(id, name.trim(), description, price, imageUrl, categoryId.trim());
        product.domainEvents.add(ProductCreated.of(id, product.categoryId));
        return product;
    }

    /**
     * Behaviour {@code update(...)} - domain-models §2.3.1. Every argument is optional; a null
     * argument leaves that field unchanged (partial update, per ProductUpdateRequest).
     * Emits {@link ProductUpdated}.
     *
     * @throws InactiveProductException if the product has been deactivated (invariant 2)
     */
    public void update(String name, String description, Money price, String imageUrl, String categoryId) {
        if (status == ProductStatus.INACTIVE) {
            throw new InactiveProductException(id);
        }
        if (name != null) {
            requireText(name, "name must not be blank");
            this.name = name.trim();
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            this.price = price;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (categoryId != null) {
            requireText(categoryId, "categoryId must not be blank");
            this.categoryId = categoryId.trim();
        }
        this.updatedAt = Instant.now();
        this.domainEvents.add(ProductUpdated.of(id));
    }

    /**
     * Behaviour {@code deactivate()} - domain-models §2.3.1. Emits {@link ProductDeactivated}.
     * Idempotent: deactivating an already-inactive product is a no-op, since the OpenAPI
     * contract lists no 409 response for this operation.
     */
    public void deactivate() {
        if (status == ProductStatus.INACTIVE) {
            return;
        }
        this.status = ProductStatus.INACTIVE;
        this.updatedAt = Instant.now();
        this.domainEvents.add(ProductDeactivated.of(id));
    }

    /** Returns the pending events and clears them, so each event is published at most once. */
    public List<Object> pullDomainEvents() {
        List<Object> pending = List.copyOf(domainEvents);
        domainEvents.clear();
        return pending;
    }

    public List<Object> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Raised when invariant 2 is violated; mapped to HTTP 409 ProductStateViolation. */
    public static class InactiveProductException extends RuntimeException {
        public InactiveProductException(String productId) {
            super("An INACTIVE product cannot be updated: " + productId);
        }
    }
}
