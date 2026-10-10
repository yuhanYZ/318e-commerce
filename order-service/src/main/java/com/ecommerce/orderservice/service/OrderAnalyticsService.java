package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderAnalyticsWindow;
import com.ecommerce.orderservice.dto.MoneyDto;
import com.ecommerce.orderservice.dto.OrderAnalyticsResponse;
import com.ecommerce.orderservice.exception.ApiException;
import com.ecommerce.orderservice.repository.OrderAnalyticsWindowRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * O6 read-model updater and query service for rolling order volume/revenue analytics.
 */
@Service
public class OrderAnalyticsService {

    private final OrderAnalyticsWindowRepository analyticsRepository;
    private final int defaultWindowMinutes;
    private final String defaultCurrency;

    public OrderAnalyticsService(OrderAnalyticsWindowRepository analyticsRepository,
                                 @Value("${app.analytics.window-minutes:15}") int defaultWindowMinutes,
                                 @Value("${app.analytics.default-currency:AUD}") String defaultCurrency) {
        this.analyticsRepository = analyticsRepository;
        this.defaultWindowMinutes = defaultWindowMinutes;
        this.defaultCurrency = defaultCurrency;
    }

    /** Updates the one-minute projection in the same transaction that creates the order. */
    public void recordOrderPlaced(Order order, Instant occurredAt) {
        Instant bucketStart = occurredAt.truncatedTo(ChronoUnit.MINUTES);
        OrderAnalyticsWindow bucket = analyticsRepository.findById(bucketStart)
                .orElseGet(() -> new OrderAnalyticsWindow(bucketStart, order.getTotalCurrency(), occurredAt));
        bucket.increment(order.getTotalAmount(), order.getTotalCurrency(), occurredAt);
        analyticsRepository.save(bucket);
    }

    @Transactional(readOnly = true)
    public OrderAnalyticsResponse getRealtimeAnalytics(Integer requestedWindowMinutes) {
        int windowMinutes = requestedWindowMinutes != null ? requestedWindowMinutes : defaultWindowMinutes;
        if (windowMinutes < 1 || windowMinutes > 1440) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ValidationFailed",
                    "windowMinutes must be between 1 and 1440");
        }

        Instant generatedAt = Instant.now();
        Instant windowStart = generatedAt.minus(windowMinutes, ChronoUnit.MINUTES);
        List<OrderAnalyticsWindow> buckets =
                analyticsRepository.findByWindowStartGreaterThanEqualAndWindowStartLessThanEqual(
                        windowStart, generatedAt);

        long orderCount = buckets.stream().mapToLong(OrderAnalyticsWindow::getOrderCount).sum();
        BigDecimal total = buckets.stream()
                .map(OrderAnalyticsWindow::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = buckets.stream()
                .findFirst()
                .map(OrderAnalyticsWindow::getTotalCurrency)
                .orElse(defaultCurrency);
        BigDecimal average = orderCount == 0
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        return new OrderAnalyticsResponse(windowMinutes, windowStart, generatedAt, orderCount,
                new MoneyDto(total, currency), new MoneyDto(average, currency), generatedAt);
    }
}
