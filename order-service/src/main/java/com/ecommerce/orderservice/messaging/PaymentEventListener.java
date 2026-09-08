package com.ecommerce.orderservice.messaging;

import com.ecommerce.orderservice.event.PaymentConfirmedEvent;
import com.ecommerce.orderservice.event.PaymentFailedEvent;
import com.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for O5 (specs/2-user-stories.md): reacts to payment outcomes
 * published by Payment Service (not yet built in this repo) to move an order
 * out of PENDING. No broker is provisioned yet (see OrderEventPublisher), so
 * these listeners simply won't receive anything until both a real broker and
 * payment-service exist - Spring Kafka retries connecting in the background
 * without blocking application startup or tests (org.apache.kafka logging is
 * set to ERROR in application.yml to keep that retry noise out of test output).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final OrderService orderService;

    @KafkaListener(topics = "payment-confirmed", groupId = "order-service")
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        log.info("Received PaymentConfirmedEvent for orderId={}", event.getOrderId());
        orderService.markPaid(event.getOrderId());
    }

    @KafkaListener(topics = "payment-failed", groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for orderId={}: {}", event.getOrderId(), event.getReason());
        orderService.markPaymentFailed(event.getOrderId());
    }
}
