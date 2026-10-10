package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit record of a manual stock adjustment — specs/iter2/3-domain-models-inventory-
 * service-ddd.md §2.1.1. Fully derivable from {@code StockAdjusted} events; kept as a convenience
 * entity for direct audit queries rather than replaying the event stream.
 */
@Entity
@Table(name = "stock_adjustment_log")
public class StockAdjustmentLog {

    @Id
    private String id;

    @Convert(converter = StockItemId.JpaConverter.class)
    @Column(name = "stock_item_id", nullable = false)
    private StockItemId stockItemId;

    @Column(name = "adjusted_by", nullable = false)
    private String adjustedBy;

    @Column(nullable = false)
    private int delta;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockAdjustmentLog() {
        // JPA
    }

    public StockAdjustmentLog(String id, StockItemId stockItemId, String adjustedBy, int delta, String reason,
                               Instant createdAt) {
        this.id = id;
        this.stockItemId = stockItemId;
        this.adjustedBy = adjustedBy;
        this.delta = delta;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public StockItemId getStockItemId() {
        return stockItemId;
    }

    public String getAdjustedBy() {
        return adjustedBy;
    }

    public int getDelta() {
        return delta;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
