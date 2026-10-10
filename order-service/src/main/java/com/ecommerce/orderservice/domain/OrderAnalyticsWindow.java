package com.ecommerce.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CQRS read-side projection for O6: one row per minute of placed-order volume and revenue.
 */
@Entity
@Table(name = "order_analytics_window")
public class OrderAnalyticsWindow {

    @Id
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(name = "total_revenue", nullable = false)
    private BigDecimal totalRevenue;

    @Column(name = "total_currency", nullable = false)
    private String totalCurrency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderAnalyticsWindow() {
        // JPA
    }

    public OrderAnalyticsWindow(Instant windowStart, String totalCurrency, Instant updatedAt) {
        this.windowStart = windowStart;
        this.orderCount = 0;
        this.totalRevenue = BigDecimal.ZERO;
        this.totalCurrency = totalCurrency;
        this.updatedAt = updatedAt;
    }

    public void increment(BigDecimal amount, String currency, Instant updatedAt) {
        this.orderCount += 1;
        this.totalRevenue = this.totalRevenue.add(amount);
        this.totalCurrency = currency;
        this.updatedAt = updatedAt;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public String getTotalCurrency() {
        return totalCurrency;
    }
}
