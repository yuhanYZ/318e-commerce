package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderAnalyticsWindow;
import com.ecommerce.orderservice.repository.OrderAnalyticsWindowRepository;
import org.springframework.context.annotation.Profile;
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
 *
 * <p><b>Fallback path.</b> When the "kafka" profile is active, the projection is maintained by the
 * Kafka Streams topology instead ({@code analytics.OrderAnalyticsTopologyConfig}) — the real-time
 * stream-processing implementation the specification describes — and this in-process updater is not
 * even instantiated, so the two can never double-count. It remains here for the profile-less case
 * (the contract test suite, which by design runs with Kafka disabled) so that the O6 read path stays
 * exercisable end-to-end without a broker.
 */
@Service
@Profile("!kafka")
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
