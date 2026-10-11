package com.ecommerce.productservice.domain;

import com.ecommerce.productservice.exception.ApiException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code ProductAggregate} (root: Product) —
 * specs/iter2/3-domain-models-product-service-ddd.md §2.3.1. State changes go through the
 * behaviors below, each of which raises the domain event the specification assigns to it.
 *
 * <p>Stock quantity is intentionally NOT stored here — it belongs to Inventory Service's
 * {@code StockItem} and is retrieved via inter-service REST call (P2).
 *
 * <p>Invariants: (1) {@code price.amount >= 0} — enforced by {@link Money}; (2) INACTIVE is a
 * terminal listing state, so {@link #update} on an INACTIVE product is rejected with 409;
 * (3) "categoryId must reference an existing Category" is <b>not</b> enforced in this iteration —
 * the OpenAPI contract exposes no category write endpoint, so a caller has no way to satisfy the
 * rule (see {@link Category}).
 */
@Entity
@Table(name = "product")
public class Product {

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id", nullable = false))
    private ProductId id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "price_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "price_currency", nullable = false))
    })
    private Money price;

    @Column(name = "image_url")
    private String imageUrl;

    @Convert(converter = CategoryId.JpaConverter.class)
    @Column(name = "category_id", nullable = false)
    private CategoryId categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected Product() {
        // JPA
    }

    /** P4 — create. Emits {@code ProductCreated}; a new listing starts ACTIVE and is not updatable-before. */
    public static Product create(ProductId id, String name, String description, Money price, String imageUrl,
                                 CategoryId categoryId, Instant now) {
        Product product = new Product();
        product.id = id;
        product.name = name;
        product.description = description;
        product.price = price;
        product.imageUrl = imageUrl;
        product.categoryId = categoryId;
        product.status = ProductStatus.ACTIVE;
        product.createdAt = now;
        product.updatedAt = now;
        product.raise(new ProductCreated(id, categoryId, now));
        return product;
    }

    /**
     * P4 — partial update (only non-null parameters are applied). Emits {@code ProductUpdated}.
     * Rejected with 409 while the product is INACTIVE (invariant 2 — INACTIVE is terminal).
     */
    public ProductUpdated update(String name, String description, Money price, String imageUrl,
                                 CategoryId categoryId, Instant now) {
        if (status == ProductStatus.INACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "ProductStateViolation",
                    "An INACTIVE product cannot be updated");
        }
        if (name != null) {
            this.name = name;
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
            this.categoryId = categoryId;
        }
        this.updatedAt = now;
        ProductUpdated event = new ProductUpdated(id, now);
        raise(event);
        return event;
    }

    /** P4 — deactivate the listing. Emits {@code ProductDeactivated}. */
    public ProductDeactivated deactivate(Instant now) {
        this.status = ProductStatus.INACTIVE;
        this.updatedAt = now;
        ProductDeactivated event = new ProductDeactivated(id, now);
        raise(event);
        return event;
    }

    /** Drains the events raised by the behaviors above so the command service can dispatch them. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    public ProductId getId() {
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

    public CategoryId getCategoryId() {
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
}
