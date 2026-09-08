package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderItem;
import com.ecommerce.orderservice.domain.OrderStatus;
import com.ecommerce.orderservice.dto.external.ProductInfo;
import com.ecommerce.orderservice.dto.external.StockResponse;
import com.ecommerce.orderservice.dto.request.CreateOrderRequest;
import com.ecommerce.orderservice.dto.request.OrderItemRequest;
import com.ecommerce.orderservice.dto.response.OrderItemResponse;
import com.ecommerce.orderservice.dto.response.OrderPageResponse;
import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.event.OrderCancelledEvent;
import com.ecommerce.orderservice.event.OrderCreatedEvent;
import com.ecommerce.orderservice.event.OrderItemPayload;
import com.ecommerce.orderservice.exception.InsufficientStockException;
import com.ecommerce.orderservice.exception.InvalidOrderStateException;
import com.ecommerce.orderservice.exception.OrderAccessDeniedException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.messaging.OrderEventPublisher;
import com.ecommerce.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Business logic for the Order Service. Implements O1-O5 from
 * specs/2-user-stories.md, per the Service Layer responsibilities in
 * specs/1-technical-architecture.md (cross-service coordination with
 * Inventory/Product Service, Kafka event publishing).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    /** O4: only orders in one of these statuses can still be cancelled. */
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = EnumSet.of(OrderStatus.PENDING, OrderStatus.PAID);

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final ProductClient productClient;
    private final OrderEventPublisher orderEventPublisher;

    /** O1: place a new order - checks stock, snapshots price, persists PENDING, publishes OrderCreatedEvent. */
    @Transactional
    public OrderResponse createOrder(String userId, CreateOrderRequest request) {
        for (OrderItemRequest itemRequest : request.getItems()) {
            StockResponse stock = inventoryClient.getStock(itemRequest.getProductId());
            if (!stock.isAvailable() || stock.getQuantityAvailable() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for productId=" + itemRequest.getProductId());
            }
        }

        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductInfo product = productClient.getProduct(itemRequest.getProductId());
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            total = total.add(lineTotal);

            OrderItem item = OrderItem.builder()
                    .productId(itemRequest.getProductId())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
            order.addItem(item);
        }
        order.setTotalAmount(total);

        order = orderRepository.save(order);

        orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                order.getId(), order.getUserId(), toEventItems(order.getItems())));

        return toResponse(order);
    }

    /** O2: get a single order, enforcing ownership (404 if it doesn't exist, 403 if it belongs to someone else). */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(String userId, String orderId) {
        Order order = findOwnedOrder(userId, orderId);
        return toResponse(order);
    }

    /** O3: current user's order history, newest first. */
    @Transactional(readOnly = true)
    public OrderPageResponse listMyOrders(String userId, int page) {
        Page<Order> result = orderRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(page, 0), 20));
        List<OrderResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new OrderPageResponse(content, result.getNumber(), result.getTotalElements());
    }

    /** O4: cancel an order before it ships; publishes OrderCancelledEvent on success. */
    @Transactional
    public OrderResponse cancelOrder(String userId, String orderId) {
        Order order = findOwnedOrder(userId, orderId);
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " cannot be cancelled from status " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        orderEventPublisher.publishOrderCancelled(new OrderCancelledEvent(
                order.getId(), order.getUserId(), toEventItems(order.getItems())));

        return toResponse(order);
    }

    /** O5: consumes PaymentConfirmedEvent (via PaymentEventListener). PENDING -> PAID only. */
    @Transactional
    public void markPaid(String orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);
            } else {
                log.warn("Ignoring PaymentConfirmedEvent for orderId={}: status is {}, not PENDING",
                        orderId, order.getStatus());
            }
        }, () -> log.warn("Ignoring PaymentConfirmedEvent for unknown orderId={}", orderId));
    }

    /** O5: consumes PaymentFailedEvent (via PaymentEventListener). PENDING -> PAYMENT_FAILED only. */
    @Transactional
    public void markPaymentFailed(String orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PAYMENT_FAILED);
                orderRepository.save(order);
            } else {
                log.warn("Ignoring PaymentFailedEvent for orderId={}: status is {}, not PENDING",
                        orderId, order.getStatus());
            }
        }, () -> log.warn("Ignoring PaymentFailedEvent for unknown orderId={}", orderId));
    }

    private Order findOwnedOrder(String userId, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        if (!order.getUserId().equals(userId)) {
            throw new OrderAccessDeniedException("Order " + orderId + " does not belong to the current user");
        }
        return order;
    }

    private List<OrderItemPayload> toEventItems(List<OrderItem> items) {
        return items.stream()
                .map(i -> new OrderItemPayload(i.getProductId(), i.getQuantity()))
                .toList();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt());
    }
}
