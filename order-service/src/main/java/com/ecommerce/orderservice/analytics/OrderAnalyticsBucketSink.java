package com.ecommerce.orderservice.analytics;

import java.time.Instant;

/**
 * Where the Kafka Streams topology publishes each completed window aggregate. A separate seam from
 * the topology itself so the topology can be driven by {@code TopologyTestDriver} with a recording
 * sink, with no Spring context and no broker.
 */
@FunctionalInterface
public interface OrderAnalyticsBucketSink {

    void upsert(Instant windowStart, OrderAnalyticsBucket bucket);
}
