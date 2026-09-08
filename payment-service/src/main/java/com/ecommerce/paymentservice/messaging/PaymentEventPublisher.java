package com.ecommerce.paymentservice.messaging;

import com.ecommerce.paymentservice.event.PaymentConfirmedEvent;
import com.ecommerce.paymentservice.event.PaymentFailedEvent;
import com.ecommerce.paymentservice.event.RefundProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for payment/refund events (PAY1, PAY3), mirroring
 * order-service's OrderEventPublisher: no broker is provisioned in this repo
 * yet, so max.block.ms is kept short (application.yml) and every publish is
 * wrapped in try/catch so a missing broker never fails a payment/refund that
 * has already been persisted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private static final String PAYMENT_CONFIRMED_TOPIC = "payment-confirmed";
    private static final String PAYMENT_FAILED_TOPIC = "payment-failed";
    private static final String REFUND_PROCESSED_TOPIC = "refund-processed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** PAY1 success. Consumed by order-service's O5. */
    public void publishPaymentConfirmed(PaymentConfirmedEvent event) {
        publish(PAYMENT_CONFIRMED_TOPIC, event.getOrderId(), event);
    }

    /** PAY1 decline. Consumed by order-service's O5 and inventory-service's I5. */
    public void publishPaymentFailed(PaymentFailedEvent event) {
        publish(PAYMENT_FAILED_TOPIC, event.getOrderId(), event);
    }

    /** PAY3 success (no consumer built yet - see RefundProcessedEvent's javadoc). */
    public void publishRefundProcessed(RefundProcessedEvent event) {
        publish(REFUND_PROCESSED_TOPIC, event.getOrderId(), event);
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
