package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks the current stock level for one product (I1, I2, I5).
 * Maps to the {@code StockItem} domain class in specs/3-domain-models.md
 * section 5.1. {@code productId} is a cross-service reference only (Product
 * Service owns the catalog) - inventory is deliberately NOT stored on
 * Product itself, per that spec's note under section 3.1.
 */
@Entity
@Table(name = "stock_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockItem {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String productId;

    @Column(nullable = false)
    private int quantityAvailable;

    /**
     * Per-item threshold (specs/3-domain-models.md section 5.1 flags "global vs
     * per-product, TBD" as an open question); resolved here as per-item so each
     * StockItem can carry its own value, defaulted from
     * app.inventory.default-low-stock-threshold when first created (I3).
     */
    @Column(nullable = false)
    private int lowStockThreshold;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
