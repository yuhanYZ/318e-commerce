package com.ecommerce.inventoryservice.analytics;

import com.ecommerce.inventoryservice.integration.StockChangedEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
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

import java.util.function.Consumer;

/**
 * I4 — the low-stock alert projection as a stream-processing topology: the {@code stock.changed}
 * stream is folded into the latest available quantity per product, the global threshold is applied
 * to that state, and every verdict is handed to {@link LowStockAlertSink}
 * (specs/iter2/6-cqrs-command-query.md section 8, 5-event-sourcing.md section 3.5).
 *
 * <p>Two shape notes, the same two as O6's topology. First, it is a
 * {@code Consumer<KStream<..>>} that sinks straight into the alert store, so no output topic is
 * introduced. Second, every verdict is emitted rather than only the below-threshold ones, because
 * clearing the threshold again is exactly what resolves an open alert.
 *
 * <p><b>Active only under the "kafka" profile.</b> Without that profile (i.e. during
 * {@code mvn test}) this bean does not exist and {@code InventoryCommandService} keeps evaluating
 * the threshold inline in the write transaction, exactly as before — which is what keeps the I4
 * contract tests runnable without a broker. The two paths are mutually exclusive, never
 * simultaneous.
 */
@Configuration
@Profile("kafka")
public class LowStockAlertTopologyConfig {

    private static final String STOCK_STORE = "inventory-stock-by-product";

    private final int lowStockThreshold;

    public LowStockAlertTopologyConfig(@Value("${app.inventory.low-stock-threshold}") int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    @Bean
    public Consumer<KStream<String, StockChangedEvent>> lowStockAlertTopology(LowStockAlertSink alertSink) {
        return stockEvents -> stockEvents
                .groupBy((key, event) -> event.productId(),
                        Grouped.with(Serdes.String(), new JsonSerde<>(StockChangedEvent.class)))
                .aggregate(
                        ProductStock::empty,
                        (productId, event, stock) -> stock.apply(event),
                        Materialized.<String, ProductStock, KeyValueStore<Bytes, byte[]>>as(STOCK_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(ProductStock.class)))
                .toStream()
                .mapValues(stock -> StockAlertState.of(stock, lowStockThreshold))
                .foreach((productId, state) -> alertSink.apply(state));
    }

    /** Explicit input serde for the manual (test) wiring of this topology; the binder supplies it at runtime. */
    public static Consumed<String, StockChangedEvent> consumed() {
        return Consumed.with(Serdes.String(), new JsonSerde<>(StockChangedEvent.class));
    }
}
