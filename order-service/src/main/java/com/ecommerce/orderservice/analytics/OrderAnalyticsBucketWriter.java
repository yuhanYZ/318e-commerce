package com.ecommerce.orderservice.analytics;

import com.ecommerce.orderservice.domain.Money;
import com.ecommerce.orderservice.domain.OrderAnalyticsWindow;
import com.ecommerce.orderservice.repository.OrderAnalyticsWindowRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Writes a window aggregate produced by the Kafka Streams topology into the
 * {@code order_analytics_window} read model.
 *
 * <p>The write is a <b>replace</b>, not an increment: the topology always hands over the complete
 * aggregate for the window, so replaying the same window (at-least-once delivery, or a topology
 * restart) rewrites the same row with the same value instead of double-counting.
 */
@Component
public class OrderAnalyticsBucketWriter implements OrderAnalyticsBucketSink {

    private final OrderAnalyticsWindowRepository analyticsRepository;

    public OrderAnalyticsBucketWriter(OrderAnalyticsWindowRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Override
    public void upsert(Instant windowStart, OrderAnalyticsBucket bucket) {
        Instant updatedAt = Instant.ofEpochMilli(bucket.lastEventEpochMilli());
        OrderAnalyticsWindow window = analyticsRepository.findById(windowStart)
                .orElseGet(() -> new OrderAnalyticsWindow(windowStart, bucket.currency(), updatedAt));
        window.replace(bucket.orderCount(), new Money(bucket.totalAmount(), bucket.currency()), updatedAt);
        analyticsRepository.save(window);
    }
}
