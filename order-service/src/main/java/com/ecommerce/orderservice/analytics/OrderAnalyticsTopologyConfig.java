package com.ecommerce.orderservice.analytics;

import com.ecommerce.orderservice.integration.OrderCreatedEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * O6 — the real-time order volume/revenue projection as an actual stream-processing topology: a
 * one-minute <b>tumbling window</b> over the {@code order.created} stream, aggregated into
 * {@code orderCount} / {@code totalRevenue} buckets
 * (specs/iter2/3-domain-models-order-service-ddd.md §2.6.1, 6-cqrs-command-query.md §4.2/§7).
 *
 * <p>The Kafka Streams library is used directly rather than through Spring Cloud Stream's Kafka
 * Streams binder. The binder cannot coexist with the Kafka binder in one application — both
 * contribute a {@code KafkaBinderConfigurationProperties} bean and the streams binder's
 * {@code KafkaStreamsJaasConfiguration} requires exactly one — while this service needs the Kafka
 * binder for its O5 command consumers. Running the library directly keeps the topology inside the
 * service that owns the read model: no extra topic, no binder indirection, and the aggregate is
 * sunk straight into {@code order_analytics_window}.
 *
 * <p>Two shape choices: the topology writes the complete window value through
 * {@link OrderAnalyticsBucketSink} (so replaying a window cannot double-count), and it is created
 * only under the "kafka" profile, so {@code mvn test} never starts a Streams client and the
 * in-process fallback keeps the O6 read path exercisable without a broker.
 */
@Configuration
@Profile("kafka")
public class OrderAnalyticsTopologyConfig {

    /** The topic Order Service publishes to on O1 and this topology consumes. */
    public static final String INPUT_TOPIC = "order.created";

    private static final String WINDOW_STORE = "order-analytics-windows";

    private final OrderAnalyticsBucketSink bucketSink;
    private final String bootstrapServers;
    private final String applicationId;
    private final String stateDir;

    public OrderAnalyticsTopologyConfig(
            OrderAnalyticsBucketSink bucketSink,
            @Value("${app.analytics.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${app.analytics.application-id:order-analytics}") String applicationId,
            @Value("${app.analytics.state-dir:${java.io.tmpdir}/order-analytics-state}") String stateDir) {
        this.bucketSink = bucketSink;
        this.bootstrapServers = bootstrapServers;
        this.applicationId = applicationId;
        this.stateDir = stateDir;
    }

    /**
     * The aggregation itself, independent of how it is run — {@code OrderAnalyticsTopologyTests}
     * drives exactly this with {@code TopologyTestDriver}.
     */
    public static Consumer<KStream<String, OrderCreatedEvent>> topology(OrderAnalyticsBucketSink sink) {
        return createdOrders -> createdOrders
                .groupBy((key, event) -> OrderAnalyticsBucket.SINGLE_GROUP,
                        Grouped.with(Serdes.String(), orderSerde()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .aggregate(
                        OrderAnalyticsBucket::empty,
                        (key, event, bucket) -> bucket.plus(event),
                        Materialized.<String, OrderAnalyticsBucket, WindowStore<Bytes, byte[]>>as(WINDOW_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(OrderAnalyticsBucket.class)))
                .toStream()
                .foreach((windowedKey, bucket) -> sink.upsert(
                        Instant.ofEpochMilli(windowedKey.window().start()), bucket));
    }

    /** The source-stream definition, shared by the running topology and the test. */
    public static Consumed<String, OrderCreatedEvent> consumed() {
        return Consumed.with(Serdes.String(), orderSerde());
    }

    /** {@code KafkaStreams} is closed by the container on shutdown (destroyMethod). */
    @Bean(destroyMethod = "close")
    public KafkaStreams orderAnalyticsStreams() {
        StreamsBuilder builder = new StreamsBuilder();
        topology(bucketSink).accept(builder.stream(INPUT_TOPIC, consumed()));
        KafkaStreams streams = new KafkaStreams(builder.build(), streamsProperties());
        streams.start();
        return streams;
    }

    private Properties streamsProperties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class);
        properties.put("spring.json.trusted.packages", "com.ecommerce.orderservice.*");
        return properties;
    }

    private static JsonSerde<OrderCreatedEvent> orderSerde() {
        return new JsonSerde<>(OrderCreatedEvent.class);
    }
}
