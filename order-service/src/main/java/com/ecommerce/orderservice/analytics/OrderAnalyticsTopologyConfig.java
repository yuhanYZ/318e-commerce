package com.ecommerce.orderservice.analytics;

import com.ecommerce.orderservice.integration.OrderCreatedEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.common.utils.Bytes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * O6 — the real-time order volume/revenue projection as an actual stream-processing topology:
 * a one-minute <b>tumbling window</b> over the {@code order.created} stream, aggregated into
 * {@code orderCount} / {@code totalRevenue} buckets
 * (specs/iter2/3-domain-models-order-service-ddd.md §2.6.1, 6-cqrs-command-query.md §4.2/§7).
 *
 * <p>Two deliberate shape choices:
 * <ul>
 *   <li>It is a {@code Consumer<KStream<..>>} rather than a {@code Function<KStream, KStream>}: the
 *       aggregate is sunk into the read model directly, so no extra Kafka topic is introduced.</li>
 *   <li>The window aggregate is written as a complete value ({@link OrderAnalyticsBucketSink#upsert}),
 *       which keeps the sink idempotent under at-least-once delivery.</li>
 * </ul>
 *
 * <p><b>Active only under the "kafka" profile</b>, exactly like {@code config.KafkaConsumerConfig}:
 * with that profile off (i.e. during {@code mvn test}) this bean does not exist, so nothing tries to
 * bind to a broker. In that case the projection is maintained by the in-process updater instead —
 * see {@code service.OrderAnalyticsProjectionService}, which is {@code @Profile("!kafka")}. Exactly
 * one of the two writers is ever active.
 *
 * <p>This also fixes two defects of the in-process updater: the buckets now survive a restart of
 * this service (they are rebuilt from the stream's state store / the topic), and two instances of
 * the service no longer each keep their own partial tally — Kafka Streams partitions the windows
 * across the instances.
 */
@Configuration
@Profile("kafka")
public class OrderAnalyticsTopologyConfig {

    /** The aggregation keys every order into one group; the tumbling window does the bucketing. */
    private static final String WINDOW_STORE = "order-analytics-windows";

    @Bean
    public Consumer<KStream<String, OrderCreatedEvent>> orderAnalyticsTopology(OrderAnalyticsBucketSink bucketSink) {
        return createdOrders -> createdOrders
                .groupBy((key, event) -> OrderAnalyticsBucket.SINGLE_GROUP,
                        Grouped.with(Serdes.String(), new JsonSerde<>(OrderCreatedEvent.class)))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .aggregate(
                        OrderAnalyticsBucket::empty,
                        (key, event, bucket) -> bucket.plus(event),
                        Materialized.<String, OrderAnalyticsBucket, WindowStore<Bytes, byte[]>>as(WINDOW_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(OrderAnalyticsBucket.class)))
                .toStream()
                .foreach((windowedKey, bucket) -> bucketSink.upsert(
                        Instant.ofEpochMilli(windowedKey.window().start()), bucket));
    }

    /** Explicit input serde for the manual (test) wiring of this topology; the binder supplies it at runtime. */
    public static Consumed<String, OrderCreatedEvent> consumed() {
        return Consumed.with(Serdes.String(), new JsonSerde<>(OrderCreatedEvent.class));
    }
}
