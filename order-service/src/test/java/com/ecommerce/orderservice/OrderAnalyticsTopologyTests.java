package com.ecommerce.orderservice;

import com.ecommerce.orderservice.analytics.OrderAnalyticsBucket;
import com.ecommerce.orderservice.analytics.OrderAnalyticsTopologyConfig;
import com.ecommerce.orderservice.domain.Money;
import com.ecommerce.orderservice.integration.OrderCreatedEvent;
import com.ecommerce.orderservice.integration.OrderItemPayload;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the O6 stream-processing topology itself — the part the black-box contract suite
 * cannot reach, because those tests run with the "kafka" profile off (so the topology bean never
 * exists) and only exercise the read path.
 *
 * <p>Driven by {@code TopologyTestDriver}: no broker, no Spring context. It asserts what the
 * specification actually promises about O6 — a one-minute tumbling window that aggregates order
 * count and revenue — which is precisely what the in-process fallback cannot demonstrate.
 */
class OrderAnalyticsTopologyTests {

    private static final String INPUT_TOPIC = "order.created";
    private static final Instant FIRST_WINDOW = Instant.parse("2026-10-10T04:00:00Z");

    private final Map<Instant, OrderAnalyticsBucket> written = new HashMap<>();

    @Test
    void tumblingWindowAggregatesCountAndRevenuePerMinute() {
        try (TopologyTestDriver driver = driver()) {
            TestInputTopic<String, OrderCreatedEvent> input = inputTopic(driver);
            pipe(input, "ORD-A", "10.00", "2026-10-10T04:00:10Z");
            pipe(input, "ORD-B", "5.50", "2026-10-10T04:00:59Z");
            pipe(input, "ORD-C", "99.99", "2026-10-10T04:01:05Z");
        }

        assertThat(written).containsOnlyKeys(FIRST_WINDOW, FIRST_WINDOW.plusSeconds(60));

        OrderAnalyticsBucket firstMinute = written.get(FIRST_WINDOW);
        assertThat(firstMinute.orderCount()).isEqualTo(2L);
        assertThat(firstMinute.totalAmount()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(firstMinute.currency()).isEqualTo("AUD");

        OrderAnalyticsBucket secondMinute = written.get(FIRST_WINDOW.plusSeconds(60));
        assertThat(secondMinute.orderCount()).isEqualTo(1L);
        assertThat(secondMinute.totalAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
    }

    @Test
    void eachUpdateCarriesTheWholeWindowAggregateNotADelta() {
        try (TopologyTestDriver driver = driver()) {
            TestInputTopic<String, OrderCreatedEvent> input = inputTopic(driver);
            pipe(input, "ORD-A", "10.00", "2026-10-10T04:00:10Z");
            pipe(input, "ORD-B", "5.50", "2026-10-10T04:00:20Z");
        }

        // This is what makes the sink write safe to replay: every emission is the complete window
        // value, so OrderAnalyticsBucketWriter replaces the row instead of incrementing it.
        OrderAnalyticsBucket settled = written.get(FIRST_WINDOW);
        assertThat(settled.orderCount()).isEqualTo(2L);
        assertThat(settled.totalAmount()).isEqualByComparingTo(new BigDecimal("15.50"));
    }

    private TopologyTestDriver driver() {
        var topologyFunction = new OrderAnalyticsTopologyConfig().orderAnalyticsTopology(written::put);
        StreamsBuilder builder = new StreamsBuilder();
        topologyFunction.accept(builder.stream(INPUT_TOPIC, OrderAnalyticsTopologyConfig.consumed()));
        Topology topology = builder.build();

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "order-analytics-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, freshStateDir());
        return new TopologyTestDriver(topology, properties);
    }

    /**
     * The record timestamp must be supplied explicitly: without one, {@code pipeInput} uses the
     * driver's wall clock, which would put every record in the same "now" window and make the
     * windowing assertions meaningless.
     */
    private static void pipe(TestInputTopic<String, OrderCreatedEvent> input, String orderId,
                             String amount, String occurredAt) {
        Instant occurredAtInstant = Instant.parse(occurredAt);
        input.pipeInput(orderId, order(orderId, amount, occurredAt), occurredAtInstant);
    }

    /** A per-driver temp state dir: Kafka Streams refuses to share one between live instances. */
    private static String freshStateDir() {
        try {
            Path dir = Files.createTempDirectory("order-analytics-test-");
            dir.toFile().deleteOnExit();
            return dir.toString();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("could not create a temp state directory", ex);
        }
    }

    private static TestInputTopic<String, OrderCreatedEvent> inputTopic(TopologyTestDriver driver) {
        return driver.createInputTopic(INPUT_TOPIC, new StringSerializer(), new JsonSerializer<>());
    }

    private static OrderCreatedEvent order(String orderId, String amount, String occurredAt) {
        return new OrderCreatedEvent(orderId, "USR-BUYER", List.of(new OrderItemPayload("PRD-1", 1)),
                new Money(new BigDecimal(amount), "AUD"), Instant.parse(occurredAt));
    }
}
