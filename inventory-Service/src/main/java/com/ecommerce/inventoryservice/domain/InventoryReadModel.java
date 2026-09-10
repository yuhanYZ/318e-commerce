package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * {@code inventory_read_model} projection — specs/iter2/5-event-sourcing.md §3.5. Updated
 * synchronously in the same transaction as the event append (I2/I3/I5), so I1 reads are
 * effectively strongly consistent in this iteration.
 */
@Entity
@Table(name = "inventory_read_model")
public class InventoryReadModel {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReadModel() {
        // JPA
    }

    public InventoryReadModel(String productId, int quantityAvailable, long version, Instant updatedAt) {
        this.productId = productId;
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

    public void setQuantityAvailable(int quantityAvailable) {
        this.quantityAvailable = quantityAvailable;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
