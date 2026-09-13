package com.ecommerce.inventoryservice.domain;

import com.ecommerce.inventoryservice.domain.event.LowStockAlertResolved;
import com.ecommerce.inventoryservice.domain.event.LowStockAlertTriggered;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code LowStockAlertAggregate} (root: LowStockAlert) - domain-models §2.3.2.
 *
 * <p>CRUD, not event-sourced (5-event-sourcing.md §3.5 note). Its invariant, at most one unresolved
 * alert per stock item, spans rows, so it is enforced by LowStockAlertEvaluator checking for an
 * open alert before triggering a new one.
 */
@Entity
@Table(name = "low_stock_alert", indexes = @Index(name = "ix_alert_stock_item_open", columnList = "stock_item_id, resolved_at"))
public class LowStockAlert {

    @Id
    private String id;

    /** Same value as the product id: StockItemId and ProductId are interchangeable. */
    @Column(name = "stock_item_id", nullable = false)
    private String stockItemId;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    protected LowStockAlert() {
        // JPA
    }

    private LowStockAlert(String id, String stockItemId, Instant triggeredAt) {
        this.id = id;
        this.stockItemId = stockItemId;
        this.triggeredAt = triggeredAt;
    }

    /** Behaviour {@code trigger(stockItemId, now)}. Emits {@link LowStockAlertTriggered}. */
    public static LowStockAlert trigger(String stockItemId, Instant now) {
        if (stockItemId == null || stockItemId.isBlank()) {
            throw new IllegalArgumentException("stockItemId is required");
        }
        LowStockAlert alert = new LowStockAlert(nextId(), stockItemId, now);
        alert.domainEvents.add(new LowStockAlertTriggered(alert.id, stockItemId, now));
        return alert;
    }

    /** Behaviour {@code resolve(now)}. Emits {@link LowStockAlertResolved}. Resolving twice is a no-op. */
    public void resolve(Instant now) {
        if (!isOpen()) {
            return;
        }
        this.resolvedAt = now;
        domainEvents.add(new LowStockAlertResolved(id, stockItemId, now));
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }

    /** Returns pending events and clears them, so each is published at most once. */
    public List<Object> pullDomainEvents() {
        List<Object> pending = List.copyOf(domainEvents);
        domainEvents.clear();
        return pending;
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

    /** Same shape as product ids, e.g. ALERT-3F9A2B1C. */
    private static String nextId() {
        return "ALERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
