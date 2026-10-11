package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @Convert(converter = StockItemId.JpaConverter.class)
    @Column(name = "stock_item_id", nullable = false)
    private StockItemId stockItemId;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected LowStockAlert() {
        // JPA
    }

    /** Opens an alert. Emits {@code LowStockAlertTriggered} (spec section 2.3.2). */
    public LowStockAlert(String id, StockItemId stockItemId, Instant triggeredAt) {
        this.id = id;
        this.stockItemId = stockItemId;
        this.triggeredAt = triggeredAt;
        this.resolvedAt = null;
        raise(new LowStockAlertTriggered(id, stockItemId, triggeredAt));
    }

    public String getId() {
        return id;
    }

    public StockItemId getStockItemId() {
        return stockItemId;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    /** Resolves the alert once stock has cleared the threshold again. Emits {@code LowStockAlertResolved}. */
    public void resolve(Instant now) {
        this.resolvedAt = now;
        raise(new LowStockAlertResolved(id, stockItemId, now));
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }

    /** Drains the events raised by this aggregate so the caller can dispatch them. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void raise(DomainEvent event) {
        domainEvents.add(event);
    }
}
