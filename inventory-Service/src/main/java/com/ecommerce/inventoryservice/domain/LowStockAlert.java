package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * {@code LowStockAlertAggregate} (root: LowStockAlert) — CRUD, not event-sourced, per
 * specs/iter2/3-domain-models-inventory-service-ddd.md §2.3.2 and 5-event-sourcing.md §1/§56.
 * Invariant: at most one unresolved (resolvedAt == null) alert per stockItemId at a time.
 */
@Entity
@Table(name = "low_stock_alert")
public class LowStockAlert {

    @Id
    private String id;

    @Column(name = "stock_item_id", nullable = false)
    private String stockItemId;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected LowStockAlert() {
        // JPA
    }

    public LowStockAlert(String id, String stockItemId, Instant triggeredAt) {
        this.id = id;
        this.stockItemId = stockItemId;
        this.triggeredAt = triggeredAt;
        this.resolvedAt = null;
    }

    public String getId() {
        return id;
    }

    public String getStockItemId() {
        return stockItemId;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void resolve(Instant now) {
        this.resolvedAt = now;
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }
}
