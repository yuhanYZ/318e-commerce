package com.ecommerce.orderservice.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * CQRS read-side projection for O6 — one row per minute of placed-order volume and revenue;
 * specs/iter2/3-domain-models-order-service-ddd.md §2.6.1. A bucket is created on the first
 * {@code OrderPlaced} of its minute and incremented on each subsequent one.
 */
@Entity
@Table(name = "order_analytics_window")
public class OrderAnalyticsWindow {

    @Id
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_revenue_amount", nullable = false)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "total_revenue_currency", nullable = false))
    })
    private Money totalRevenue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderAnalyticsWindow() {
        // JPA
    }

    public OrderAnalyticsWindow(Instant windowStart, String currency, Instant updatedAt) {
        this.windowStart = windowStart;
        this.orderCount = 0;
        this.totalRevenue = Money.zero(currency);
        this.updatedAt = updatedAt;
    }

    public void increment(Money amount, Instant updatedAt) {
        this.orderCount += 1;
        this.totalRevenue = this.totalRevenue.add(amount);
        this.updatedAt = updatedAt;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public Money getTotalRevenue() {
        return totalRevenue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
