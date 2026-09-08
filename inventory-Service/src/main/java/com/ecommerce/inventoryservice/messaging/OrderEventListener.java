package com.ecommerce.inventoryservice.messaging;

import com.ecommerce.inventoryservice.event.OrderCancelledEvent;
import com.ecommerce.inventoryservice.event.OrderCreatedEvent;
import com.ecommerce.inventoryservice.event.PaymentFailedEvent;
import com.ecommerce.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for I2 and I5. No broker is provisioned yet (see
 * InventoryEventPublisher), so these listeners simply won't receive anything
 * until both a real broker and the publishing services exist (order-service
 * is built; Payment Service is not yet) - Spring Kafka retries connecting in
 * the background without blocking application startup or tests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final InventoryService inventoryService;

    /** I2. */
    @KafkaListener(topics = "order-created", groupId = "inventory-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for orderId={}", event.getOrderId());
        inventoryService.deductForOrder(event.getOrderId(), event.getItems());
    }

    /** I5. */
    @KafkaListener(topics = "order-cancelled", groupId = "inventory-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelledEvent for orderId={}", event.getOrderId());
        inventoryService.releaseForOrder(event.getOrderId(), event.getItems());
    }

    /** I5 (alternative trigger, per User Story I5 step i). */
    @KafkaListener(topics = "payment-failed", groupId = "inventory-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for orderId={}: {}", event.getOrderId(), event.getReason());
        inventoryService.releaseForOrder(event.getOrderId(), event.getItems());
    }
}
