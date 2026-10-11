package com.ecommerce.inventoryservice;

import com.ecommerce.inventoryservice.analytics.LowStockAlertTopologyConfig;
import com.ecommerce.inventoryservice.analytics.StockAlertState;
import com.ecommerce.inventoryservice.integration.StockChangedEvent;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the I4 stream-processing topology — the part the black-box contract suite cannot
 * reach, because those tests run with the "kafka" profile off (so the topology bean never exists)
 * and only exercise the inline fallback.
 *
 * <p>Driven by {@code TopologyTestDriver}: no broker, no Spring context.
 */
class LowStockAlertTopologyTests {

    private static final String INPUT_TOPIC = "stock.changed";
    private static final int THRESHOLD = 5;

    private final List<StockAlertState> emitted = new ArrayList<>();

    @Test
    void foldsStockEventsIntoLatestQuantityAndThresholdVerdict() {
        try (TopologyTestDriver driver = driver()) {
            TestInputTopic<String, StockChangedEvent> input = inputTopic(driver);
            pipe(input, adjusted("PRD-1", 10, "2026-10-10T04:00:01Z"));
            pipe(input, deducted("PRD-1", 6, "2026-10-10T04:00:02Z"));
            pipe(input, released("PRD-1", 6, "2026-10-10T04:00:03Z"));
        }

        assertThat(emitted).extracting(StockAlertState::productId)
                .containsExactly("PRD-1", "PRD-1", "PRD-1");
        assertThat(emitted).extracting(StockAlertState::quantityAvailable)
                .containsExactly(10, 4, 10);
        // 10 AUD ... restock 10 -> 10 (above), deduct 6 -> 4 (below), release 6 -> 10 (above again).
        // The verdict is emitted on every update precisely so the last one can resolve an open alert.
        assertThat(emitted).extracting(StockAlertState::belowThreshold)
                .containsExactly(false, true, false);
    }

    @Test
    void keepsOneIndependentRunningQuantityPerProduct() {
        try (TopologyTestDriver driver = driver()) {
            TestInputTopic<String, StockChangedEvent> input = inputTopic(driver);
            pipe(input, adjusted("PRD-1", 10, "2026-10-10T04:00:01Z"));
            pipe(input, adjusted("PRD-2", 3, "2026-10-10T04:00:02Z"));
            pipe(input, deducted("PRD-1", 10, "2026-10-10T04:00:03Z"));
        }

        assertThat(emitted).extracting(StockAlertState::productId)
                .containsExactly("PRD-1", "PRD-2", "PRD-1");
        assertThat(emitted).extracting(StockAlertState::quantityAvailable)
                .containsExactly(10, 3, 0);
        assertThat(emitted).extracting(StockAlertState::belowThreshold)
                .containsExactly(false, true, true);
    }

    private TopologyTestDriver driver() {
        var topologyFunction = LowStockAlertTopologyConfig.topology(emitted::add, THRESHOLD);
        StreamsBuilder builder = new StreamsBuilder();
        topologyFunction.accept(builder.stream(INPUT_TOPIC, LowStockAlertTopologyConfig.consumed()));
        Topology topology = builder.build();

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "inventory-alerts-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, freshStateDir());
        return new TopologyTestDriver(topology, properties);
    }

    private static TestInputTopic<String, StockChangedEvent> inputTopic(TopologyTestDriver driver) {
        return driver.createInputTopic(INPUT_TOPIC, new StringSerializer(), new JsonSerializer<>());
    }

    /**
     * The record timestamp must be supplied explicitly: without one, {@code pipeInput} uses the
     * driver's wall clock, which bears no relation to the event times this test asserts on.
     */
    private static void pipe(TestInputTopic<String, StockChangedEvent> input, StockChangedEvent event) {
        input.pipeInput(event.productId(), event, event.occurredAt());
    }

    private static StockChangedEvent adjusted(String productId, int delta, String occurredAt) {
        return new StockChangedEvent(productId, "StockAdjusted", null, delta, Instant.parse(occurredAt));
    }

    private static StockChangedEvent deducted(String productId, int quantity, String occurredAt) {
        return new StockChangedEvent(productId, "StockDeducted", quantity, null, Instant.parse(occurredAt));
    }

    private static StockChangedEvent released(String productId, int quantity, String occurredAt) {
        return new StockChangedEvent(productId, "StockReleased", quantity, null, Instant.parse(occurredAt));
    }

    /** A per-driver temp state dir: Kafka Streams refuses to share one between live instances. */
    private static String freshStateDir() {
        try {
            Path dir = Files.createTempDirectory("inventory-alerts-test-");
            dir.toFile().deleteOnExit();
            return dir.toString();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("could not create a temp state directory", ex);
        }
    }
}
