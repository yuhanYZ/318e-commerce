package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Event store row for {@code StockItemAggregate} — specs/iter2/5-event-sourcing.md §3.1/§3.4.
 * Stream key = aggregateType + aggregateId; aggregateId is the tracked productId (== StockItemId).
 * The unique constraint on (aggregate_id, aggregate_version) is the optimistic-concurrency guard.
 *
 * <p>The typed columns below (delta/quantity/orderId/reason/adjustedBy) hold the fields actually
 * needed to replay the stream; {@code payload} additionally carries a JSON rendering of the same
 * data for audit fidelity to the spec's generic "payload: JSON" field, but replay reads the typed
 * columns only.
 */
@Entity
@Table(name = "stock_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_event_agg_version", columnNames = {"aggregate_id", "aggregate_version"})
})
public class StockEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "delta_value")
    private Integer delta;

    @Column(name = "quantity_value")
    private Integer quantity;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "adjusted_by")
    private String adjustedBy;

    @Column(name = "payload", length = 2000)
    private String payload;

    protected StockEvent() {
        // JPA
    }

    public StockEvent(String eventId, String aggregateId, String eventType, long aggregateVersion,
                       Instant occurredAt, Integer delta, Integer quantity, String orderId, String reason,
                       String adjustedBy, String payload) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.aggregateType = "StockItemAggregate";
        this.eventType = eventType;
        this.aggregateVersion = aggregateVersion;
        this.occurredAt = occurredAt;
        this.delta = delta;
        this.quantity = quantity;
        this.orderId = orderId;
        this.reason = reason;
        this.adjustedBy = adjustedBy;
        this.payload = payload;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Integer getDelta() {
        return delta;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }

    public String getAdjustedBy() {
        return adjustedBy;
    }

    public String getPayload() {
        return payload;
    }
}
