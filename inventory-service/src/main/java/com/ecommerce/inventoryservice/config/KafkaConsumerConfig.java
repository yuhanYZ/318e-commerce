package com.ecommerce.inventoryservice.config;

import com.ecommerce.inventoryservice.integration.OrderCancelledEvent;
import com.ecommerce.inventoryservice.integration.OrderCreatedEvent;
import com.ecommerce.inventoryservice.integration.OrderItemPayload;
import com.ecommerce.inventoryservice.integration.PaymentFailedEvent;
import com.ecommerce.inventoryservice.service.InventoryCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.function.Consumer;

/**
 * I2 and I5 Kafka consumers - 6-cqrs-command-query.md §8.
 *
 * <p>Only exists under the kafka profile, so the default profile and the test suite never create a
 * consumer. Bean names must match {@code spring.cloud.function.definition} in application.yml.
 *
 * <p>These are thin adapters: they unpack the integration event and delegate each order line to the
 * command service, which owns idempotency and the rejection rules.
 */
@Configuration
@Profile("kafka")
public class KafkaConsumerConfig {

    /** I2: order.created, deduct each line. */
    @Bean
    public Consumer<OrderCreatedEvent> deductStockOnOrderCreated(InventoryCommandService commandService) {
        return event -> linesOf(event.items()).forEach(item ->
                commandService.deductStock(event.orderId(), item.productId(), item.quantity()));
    }

    /** I5: order.cancelled, release each product. Quantities come from recorded deductions, not the payload. */
    @Bean
    public Consumer<OrderCancelledEvent> releaseStockOnOrderCancelled(InventoryCommandService commandService) {
        return event -> linesOf(event.items()).stream()
                .map(OrderItemPayload::productId)
                .distinct()
                .forEach(productId -> commandService.releaseStock(event.orderId(), productId));
    }

    /** I5: payment.failed, which carries only an order id. */
    @Bean
    public Consumer<PaymentFailedEvent> releaseStockOnPaymentFailed(InventoryCommandService commandService) {
        return event -> commandService.releaseAllStockForOrder(event.orderId());
    }

    private static List<OrderItemPayload> linesOf(List<OrderItemPayload> items) {
        return items == null ? List.of() : items;
    }
}
