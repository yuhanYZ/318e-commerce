package com.ecommerce.inventoryservice.messaging;

import com.ecommerce.inventoryservice.event.StockReservationFailedEvent;
import com.ecommerce.inventoryservice.event.StockUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for inventory events (I2), mirroring order-service's
 * OrderEventPublisher: no broker is provisioned in this repo yet, so
 * max.block.ms is kept short (application.yml) and every publish is wrapped
 * in try/catch so a missing broker never fails a deduction/release that has
 * already been persisted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventPublisher {

    private static final String STOCK_UPDATED_TOPIC = "stock-updated";
    private static final String STOCK_RESERVATION_FAILED_TOPIC = "stock-reservation-failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** I2 (no consumer built yet - see StockUpdatedEvent's javadoc). */
    public void publishStockUpdated(StockUpdatedEvent event) {
        publish(STOCK_UPDATED_TOPIC, event.getProductId(), event);
    }

    /** I2 (Order Service has no consumer for this yet - see StockReservationFailedEvent's javadoc). */
    public void publishStockReservationFailed(StockReservationFailedEvent event) {
        publish(STOCK_RESERVATION_FAILED_TOPIC, event.getOrderId(), event);
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
