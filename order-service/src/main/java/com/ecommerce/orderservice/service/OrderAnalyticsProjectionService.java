package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderAnalyticsWindow;
import com.ecommerce.orderservice.repository.OrderAnalyticsWindowRepository;
import org.springframework.beans.factory.annotation.Value;
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
 * <p><b>Fallback path.</b> When the "kafka" profile is active the projection is maintained by the
 * Kafka Streams topology instead ({@code analytics.OrderAnalyticsTopologyConfig}) — the real-time
 * stream-processing implementation the specification describes — and this updater becomes a no-op,
 * so the two writers can never double-count. It is deliberately <i>not</i> removed from the context
 * under that profile: {@code OrderCommandService} depends on it unconditionally, and gating the bean
 * itself would leave the command service without a collaborator at startup. Without the profile (the
 * contract test suite, which by design runs with Kafka disabled) it does the projection inline, which
 * keeps the O6 read path exercisable end-to-end without a broker.
 */
@Service
public class OrderAnalyticsProjectionService {

    private final OrderAnalyticsWindowRepository analyticsRepository;
    private final boolean streamTopologyActive;

    public OrderAnalyticsProjectionService(OrderAnalyticsWindowRepository analyticsRepository,
                                            @Value("${app.kafka.enabled:false}") boolean streamTopologyActive) {
        this.analyticsRepository = analyticsRepository;
        this.streamTopologyActive = streamTopologyActive;
    }

    /**
     * One row per minute: the bucket is created on the first order of that minute, then incremented.
     * No-op while the Kafka Streams topology owns this projection.
     */
    public void recordOrderPlaced(Order order, Instant occurredAt) {
        if (streamTopologyActive) {
            return;
        }
        Instant bucketStart = occurredAt.truncatedTo(ChronoUnit.MINUTES);
        OrderAnalyticsWindow bucket = analyticsRepository.findById(bucketStart)
                .orElseGet(() -> new OrderAnalyticsWindow(bucketStart, order.getTotalAmount().currency(), occurredAt));
        bucket.increment(order.getTotalAmount(), occurredAt);
        analyticsRepository.save(bucket);
    }
}
