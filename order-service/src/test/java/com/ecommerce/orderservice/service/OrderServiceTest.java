package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderStatus;
import com.ecommerce.orderservice.messaging.OrderEventPublisher;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests (no Spring context, no Kafka broker) for the O5 status
 * transitions that PaymentEventListener delegates to OrderService when
 * Payment Service (not yet built) publishes payment outcomes. O5 has no REST
 * endpoint, so it isn't covered by OrderControllerIntegrationTest.
 */
class OrderServiceTest {

    private final OrderRepository orderRepository = org.mockito.Mockito.mock(OrderRepository.class);
    private final InventoryClient inventoryClient = org.mockito.Mockito.mock(InventoryClient.class);
    private final ProductClient productClient = org.mockito.Mockito.mock(ProductClient.class);
    private final OrderEventPublisher orderEventPublisher = org.mockito.Mockito.mock(OrderEventPublisher.class);

    private final OrderService orderService =
            new OrderService(orderRepository, inventoryClient, productClient, orderEventPublisher);

    @Test
    void markPaid_pendingOrder_transitionsToPaid() {
        Order order = Order.builder().id("ORD-1").userId("USR-1").status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findById("ORD-1")).thenReturn(Optional.of(order));

        orderService.markPaid("ORD-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
    }

    @Test
    void markPaid_nonPendingOrder_isIgnored() {
        Order order = Order.builder().id("ORD-1").userId("USR-1").status(OrderStatus.CANCELLED)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findById("ORD-1")).thenReturn(Optional.of(order));

        orderService.markPaid("ORD-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void markPaymentFailed_pendingOrder_transitionsToPaymentFailed() {
        Order order = Order.builder().id("ORD-1").userId("USR-1").status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findById("ORD-1")).thenReturn(Optional.of(order));

        orderService.markPaymentFailed("ORD-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(orderRepository).save(order);
    }

    @Test
    void markPaid_unknownOrder_doesNotThrow() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        orderService.markPaid("missing");

        verify(orderRepository, never()).save(any());
    }
}
