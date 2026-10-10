package com.ecommerce.inventoryservice.analytics;

import com.ecommerce.inventoryservice.integration.StockChangedEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.util.Properties;
import java.util.function.Consumer;

/**
 * I4 — the low-stock alert projection as a stream-processing topology: the {@code stock.changed}
 * stream is folded into the latest available quantity per product, the global threshold is applied
 * to that state, and every verdict is handed to {@link LowStockAlertSink}
 * (specs/iter2/6-cqrs-command-query.md section 8, 5-event-sourcing.md section 3.5).
 *
 * <p>The Kafka Streams library is used directly rather than through Spring Cloud Stream's Kafka
 * Streams binder: the binder cannot coexist with the Kafka binder in one application (both
 * contribute a {@code KafkaBinderConfigurationProperties} bean and the streams binder's
 * {@code KafkaStreamsJaasConfiguration} requires exactly one), while this service needs the Kafka
 * binder for its I2/I5 command consumers.
 *
 * <p>Every verdict is emitted rather than only the below-threshold ones, because clearing the
 * threshold again is what resolves an open alert. Created only under the "kafka" profile: without
 * it, {@code InventoryCommandService} keeps evaluating the threshold inline in the write
 * transaction, so the I4 contract tests stay runnable without a broker. The two paths are mutually
 * exclusive, never simultaneous.
 */
@Configuration
@Profile("kafka")
public class LowStockAlertTopologyConfig {

    /** The topic Inventory Service republishes its own stock events to. */
    public static final String INPUT_TOPIC = "stock.changed";

    private static final String STOCK_STORE = "inventory-stock-by-product";

    private final LowStockAlertSink alertSink;
    private final int lowStockThreshold;
    private final String bootstrapServers;
    private final String applicationId;
    private final String stateDir;

    public LowStockAlertTopologyConfig(
            LowStockAlertSink alertSink,
            @Value("${app.inventory.low-stock-threshold}") int lowStockThreshold,
            @Value("${app.alerts.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${app.alerts.application-id:inventory-alerts}") String applicationId,
            @Value("${app.alerts.state-dir:${java.io.tmpdir}/inventory-alerts-state}") String stateDir) {
        this.alertSink = alertSink;
        this.lowStockThreshold = lowStockThreshold;
        this.bootstrapServers = bootstrapServers;
        this.applicationId = applicationId;
        this.stateDir = stateDir;
    }

    /**
     * The aggregation itself, independent of how it is run — {@code LowStockAlertTopologyTests}
     * drives exactly this with {@code TopologyTestDriver}.
     */
    public static Consumer<KStream<String, StockChangedEvent>> topology(LowStockAlertSink sink,
                                                                       int lowStockThreshold) {
        return stockEvents -> stockEvents
                .groupBy((key, event) -> event.productId(),
                        Grouped.with(Serdes.String(), stockSerde()))
                .aggregate(
                        ProductStock::empty,
                        (productId, event, stock) -> stock.apply(event),
                        Materialized.<String, ProductStock, KeyValueStore<Bytes, byte[]>>as(STOCK_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(ProductStock.class)))
                .toStream()
                .mapValues(stock -> StockAlertState.of(stock, lowStockThreshold))
                .foreach((productId, state) -> sink.apply(state));
    }

    /** The source-stream definition, shared by the running topology and the test. */
    public static Consumed<String, StockChangedEvent> consumed() {
        return Consumed.with(Serdes.String(), stockSerde());
    }

    /** {@code KafkaStreams} is closed by the container on shutdown (destroyMethod). */
    @Bean(destroyMethod = "close")
    public KafkaStreams lowStockAlertStreams() {
        StreamsBuilder builder = new StreamsBuilder();
        topology(alertSink, lowStockThreshold).accept(builder.stream(INPUT_TOPIC, consumed()));
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
        properties.put("spring.json.trusted.packages", "com.ecommerce.inventoryservice.*");
        return properties;
    }

    private static JsonSerde<StockChangedEvent> stockSerde() {
        return new JsonSerde<>(StockChangedEvent.class);
    }
}
