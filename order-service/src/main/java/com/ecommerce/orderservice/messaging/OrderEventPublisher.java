package com.ecommerce.orderservice.messaging;

import com.ecommerce.orderservice.event.OrderCancelledEvent;
import com.ecommerce.orderservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for order lifecycle events, per the Infrastructure Layer
 * responsibilities (Kafka Producers/Consumers) in specs/1-technical-architecture.md.
 * No Kafka broker is provisioned in this repo yet (no docker-compose), so
 * {@code spring.kafka.producer.properties.max.block.ms} is kept short (see
 * application.yml) and every publish is wrapped in try/catch: a broker that
 * isn't running yet must never fail or hang order creation/cancellation - it
 * only means Inventory/Payment Service won't hear about it until a real broker
 * is wired up (their consumers don't exist yet either).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private static final String ORDER_CREATED_TOPIC = "order-created";
    private static final String ORDER_CANCELLED_TOPIC = "order-cancelled";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** I2 (Inventory Service consumer, not yet built) reacts to this on the order-created topic. */
    public void publishOrderCreated(OrderCreatedEvent event) {
        publish(ORDER_CREATED_TOPIC, event.getOrderId(), event);
    }

    /** I5 / PAY3 (Inventory/Payment Service consumers, not yet built) react to this on the order-cancelled topic. */
    public void publishOrderCancelled(OrderCancelledEvent event) {
        publish(ORDER_CANCELLED_TOPIC, event.getOrderId(), event);
    }

    private void publish(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event);
        } catch (Exception ex) {
            log.warn("Could not publish {} to topic {} (Kafka not reachable?): {}",
                    event.getClass().getSimpleName(), topic, ex.getMessage());
        }
    }
}
