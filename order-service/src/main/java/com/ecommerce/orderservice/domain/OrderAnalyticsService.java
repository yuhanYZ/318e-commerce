package com.ecommerce.orderservice.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Domain Service — specs/iter2/3-domain-models-order-service-ddd.md §2.4.1. Sums the one-minute
 * buckets whose {@code windowStart} falls inside {@code [now - windowMinutes, now]}; buckets
 * outside the range are ignored, not deleted. <b>Pure calculation — no persistence or I/O.</b>
 *
 * <p>The trailing-window read itself (and the 1..1440 validation) belongs to the query application
 * service; this class only turns the buckets it is handed into a summary.
 */
@Component
public class OrderAnalyticsService {

    private final String defaultCurrency;

    public OrderAnalyticsService(@Value("${app.analytics.default-currency:AUD}") String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public OrderAnalyticsSummary summarize(List<OrderAnalyticsWindow> buckets, int windowMinutes, Instant now) {
        Instant windowStart = now.minus(windowMinutes, ChronoUnit.MINUTES);
        List<OrderAnalyticsWindow> inWindow = buckets.stream()
                .filter(bucket -> !bucket.getWindowStart().isBefore(windowStart))
                .filter(bucket -> !bucket.getWindowStart().isAfter(now))
                .toList();

        long orderCount = inWindow.stream().mapToLong(OrderAnalyticsWindow::getOrderCount).sum();
        Money totalRevenue = inWindow.isEmpty()
                ? Money.zero(defaultCurrency)
                : inWindow.stream()
                        .map(OrderAnalyticsWindow::getTotalRevenue)
                        .reduce(Money.zero(defaultCurrency), Money::add);
        Money averageOrderValue = orderCount == 0
                ? Money.zero(totalRevenue.currency())
                : new Money(totalRevenue.amount().divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP),
                        totalRevenue.currency());

        return new OrderAnalyticsSummary(orderCount, totalRevenue, averageOrderValue);
    }
}
