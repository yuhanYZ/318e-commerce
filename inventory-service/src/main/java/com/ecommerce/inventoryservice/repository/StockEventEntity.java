package com.ecommerce.inventoryservice.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One row of the event store - 5-event-sourcing.md §3.4.
 *
 * <p>Columns follow the spec's {@code stock_event} table. The unique constraint on
 * {@code (aggregate_id, aggregate_version)} is the optimistic-concurrency guard: two writers that
 * both loaded version N cannot both append N+1.
 *
 * <p>One addition to the spec schema: a nullable, indexed {@code order_id}. The spec requires
 * failed-payment releases to find an order's deductions from event history, and the order id
 * otherwise lives only inside the JSON payload. The payload stays the source of truth.
 */
@Entity
@Table(name = "stock_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_event_aggregate_version",
                columnNames = {"aggregate_id", "aggregate_version"}),
        indexes = @Index(name = "ix_stock_event_order", columnList = "order_id, event_type"))
public class StockEventEntity {

    @Id
    @Column(name = "event_id", length = 36)
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

    @Column(name = "payload", nullable = false, length = 4000)
    private String payload;

    @Column(name = "order_id")
    private String orderId;

    protected StockEventEntity() {
        // JPA
    }

    StockEventEntity(String eventId, String aggregateId, String aggregateType, String eventType,
                     long aggregateVersion, Instant occurredAt, String payload, String orderId) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.aggregateVersion = aggregateVersion;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.orderId = orderId;
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

    public String getPayload() {
        return payload;
    }

    public String getOrderId() {
        return orderId;
    }
}
