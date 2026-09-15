package com.ecommerce.inventoryservice.domain;

import com.ecommerce.inventoryservice.domain.event.StockAdjusted;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code StockAdjustmentLog} entity - domain-models §2.1.1. Append-only audit record of a manual
 * adjustment by an Admin.
 *
 * <p>Fully derivable from {@link StockAdjusted} events (§2.3.1 Event-Sourcing Note), so it is built
 * from one rather than from raw request fields. Kept as a convenience for readable audit queries.
 */
@Entity
@Table(name = "stock_adjustment_log")
public class StockAdjustmentLog {

    @Id
    private String id;

    @Column(name = "stock_item_id", nullable = false)
    private String stockItemId;

    @Column(name = "adjusted_by", nullable = false)
    private String adjustedBy;

    @Column(nullable = false)
    private int delta;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockAdjustmentLog() {
        // JPA
    }

    private StockAdjustmentLog(String id, String stockItemId, String adjustedBy, int delta, String reason,
                               Instant createdAt) {
        this.id = id;
        this.stockItemId = stockItemId;
        this.adjustedBy = adjustedBy;
        this.delta = delta;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static StockAdjustmentLog from(StockAdjusted event) {
        return new StockAdjustmentLog(UUID.randomUUID().toString(), event.productId(), event.adjustedBy(),
                event.delta(), event.reason(), event.occurredAt());
    }

    public String getId() {
        return id;
    }

    public String getStockItemId() {
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
