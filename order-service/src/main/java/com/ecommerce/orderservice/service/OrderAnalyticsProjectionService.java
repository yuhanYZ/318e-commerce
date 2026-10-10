package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderAnalyticsWindow;
import com.ecommerce.orderservice.repository.OrderAnalyticsWindowRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Maintains the {@code order_analytics_window} read model that backs O6 —
 * specs/iter2/3-domain-models-order-service-ddd.md §2.6.1.
 *
 * <p>This is the projection-update side only; the pure aggregation lives on the
 * {@link com.ecommerce.orderservice.domain.OrderAnalyticsService} domain service and the read
 * path lives on the query application service.
 */
@Service
public class OrderAnalyticsProjectionService {

    private final OrderAnalyticsWindowRepository analyticsRepository;

    public OrderAnalyticsProjectionService(OrderAnalyticsWindowRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    /** One row per minute: the bucket is created on the first order of that minute, then incremented. */
    public void recordOrderPlaced(Order order, Instant occurredAt) {
        Instant bucketStart = occurredAt.truncatedTo(ChronoUnit.MINUTES);
        OrderAnalyticsWindow bucket = analyticsRepository.findById(bucketStart)
                .orElseGet(() -> new OrderAnalyticsWindow(bucketStart, order.getTotalAmount().currency(), occurredAt));
        bucket.increment(order.getTotalAmount(), occurredAt);
        analyticsRepository.save(bucket);
    }
}
