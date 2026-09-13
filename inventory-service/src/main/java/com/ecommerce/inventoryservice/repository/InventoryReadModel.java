package com.ecommerce.inventoryservice.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The {@code inventory_read_model} projection - 5-event-sourcing.md §3.5, 6-cqrs-command-query.md §4.2.
 *
 * <p>Query side of the CQRS split: I1 reads only from here, never from the event stream. Updated in
 * the same transaction as each event append, so reads are consistent in practice. A row exists only
 * for tracked products, which is how an untracked product becomes a 404.
 */
@Entity
@Table(name = "inventory_read_model")
public class InventoryReadModel {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    /** aggregateVersion of the stream after the last projected event. Not a JPA optimistic lock. */
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReadModel() {
        // JPA
    }

    public InventoryReadModel(String productId) {
        this.productId = productId;
    }

    public void project(int quantityAvailable, long version, Instant updatedAt) {
        this.quantityAvailable = quantityAvailable;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantityAvailable() {
        return quantityAvailable;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
