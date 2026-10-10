package com.ecommerce.orderservice.domain;

/**
 * Result of {@link OrderAnalyticsService#summarize} —
 * specs/iter2/3-domain-models-order-service-ddd.md §2.4.1.
 */
public record OrderAnalyticsSummary(long orderCount, Money totalRevenue, Money averageOrderValue) {
}
