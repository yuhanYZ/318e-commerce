package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Money;
import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderId;
import com.ecommerce.orderservice.domain.OrderItem;
import com.ecommerce.orderservice.domain.OrderStatus;
import com.ecommerce.orderservice.domain.ProductId;
import com.ecommerce.orderservice.domain.Quantity;
import com.ecommerce.orderservice.domain.UserId;
import com.ecommerce.orderservice.dto.CreateOrderItemRequest;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.InventoryStockView;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.dto.ProductView;
import com.ecommerce.orderservice.exception.ApiException;
import com.ecommerce.orderservice.integration.OrderCancelledEvent;
import com.ecommerce.orderservice.integration.OrderCreatedEvent;
import com.ecommerce.orderservice.integration.OrderItemPayload;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command Application Service for Order Service — specs/iter2/6-cqrs-command-query.md §3.1.
 * Covers O1 (place), O4 (cancel) and O5 (consumes PaymentConfirmedEvent/PaymentFailedEvent —
 * {@link #updateOrderOnPaymentConfirmed}/{@link #updateOrderOnPaymentFailed}, invoked by the
 * {@code Consumer<T>} beans in {@code config.KafkaConsumerConfig}).
 *
 * <p>O1 validates every item (product exists + ACTIVE, sufficient stock) BEFORE building the
 * aggregate — the whole per-item loop runs first, and {@code orderRepository.save} only happens
 * once every item has passed. The aggregate then computes {@code totalAmount} itself
 * (invariant 1); this class never sets a total.
 *
 * <p><b>Kafka publishing (O1/O4) is gated by {@code app.kafka.enabled} (default {@code false}).</b>
 * It is only set {@code true} under the "kafka" Spring profile (see application.yml), which no
 * contract test ever activates — so {@link #publishOrderCreated}/{@link #publishOrderCancelled}
 * are guaranteed no-ops during {@code mvn test}.
 */
@Service
@Transactional
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final RestTemplate productRestTemplate;
    private final RestTemplate inventoryRestTemplate;
    private final String productBaseUrl;
    private final String inventoryBaseUrl;
    private final StreamBridge streamBridge;
    private final boolean kafkaEnabled;
    private final OrderAnalyticsProjectionService analyticsProjectionService;
    private final DomainEventPublisher domainEventPublisher;

    public OrderCommandService(OrderRepository orderRepository,
                                @Qualifier("productRestTemplate") RestTemplate productRestTemplate,
                                @Qualifier("inventoryRestTemplate") RestTemplate inventoryRestTemplate,
                                @Value("${app.product-service.base-url}") String productBaseUrl,
                                @Value("${app.inventory-service.base-url}") String inventoryBaseUrl,
                                StreamBridge streamBridge,
                                @Value("${app.kafka.enabled:false}") boolean kafkaEnabled,
                                OrderAnalyticsProjectionService analyticsProjectionService,
                                DomainEventPublisher domainEventPublisher) {
        this.orderRepository = orderRepository;
        this.productRestTemplate = productRestTemplate;
        this.inventoryRestTemplate = inventoryRestTemplate;
        this.productBaseUrl = productBaseUrl;
        this.inventoryBaseUrl = inventoryBaseUrl;
        this.streamBridge = streamBridge;
        this.kafkaEnabled = kafkaEnabled;
        this.analyticsProjectionService = analyticsProjectionService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /** O1 — Place an Order. */
    public OrderResponse placeOrder(String userId, CreateOrderRequest request) {
        List<OrderItem> items = new ArrayList<>();
        for (CreateOrderItemRequest itemReq : request.items()) {
            ProductView product = fetchProduct(itemReq.productId());
            if (product == null || !"ACTIVE".equals(product.status())) {
                throw stockInvariantViolation();
            }
            InventoryStockView stock = fetchStock(itemReq.productId());
            if (stock == null || stock.quantityAvailable() < itemReq.quantity()) {
                throw stockInvariantViolation();
            }
            BigDecimal unitPrice = product.price().amount();
            String itemCurrency = product.price().currency();
            items.add(new OrderItem(UUID.randomUUID().toString(), new ProductId(itemReq.productId()),
                    new Quantity(itemReq.quantity()), new Money(unitPrice, itemCurrency)));
        }

        Instant now = Instant.now();
        Order order = Order.place(new OrderId("ORD-" + shortId()), new UserId(userId), items, now);
        orderRepository.save(order);
        domainEventPublisher.publish(order.pullDomainEvents());
        analyticsProjectionService.recordOrderPlaced(order, now);
        publishOrderCreated(order);
        return OrderMapper.toResponse(order);
    }

    /** O4 — Cancel an Order. Only PENDING/PAID can be cancelled; both terminal states reject with 409. */
    public OrderResponse cancel(String orderId, String userId) {
        Order order = getOwnedOrThrow(orderId, userId);
        order.cancel(Instant.now());
        orderRepository.save(order);
        domainEventPublisher.publish(order.pullDomainEvents());
        publishOrderCancelled(order);
        return OrderMapper.toResponse(order);
    }

    /**
     * O5 — Update Order Status on Payment Event (PaymentConfirmedEvent branch). Idempotent on
     * orderId: only an order that is still PENDING transitions. A missing order, or one that has
     * already moved on (redelivered Kafka message, or a race with a cancel), is a silent no-op — a
     * Kafka consumer must never throw on a message that is already handled or irrelevant.
     */
    public void updateOrderOnPaymentConfirmed(String orderId) {
        transitionIfPending(orderId, order -> order.markPaid(Instant.now()));
    }

    /** O5 — Update Order Status on Payment Event (PaymentFailedEvent branch). Same idempotency rule. */
    public void updateOrderOnPaymentFailed(String orderId) {
        transitionIfPending(orderId, order -> order.markPaymentFailed(Instant.now()));
    }

    private void transitionIfPending(String orderId, java.util.function.Consumer<Order> transition) {
        OrderId id;
        try {
            id = new OrderId(orderId);
        } catch (IllegalArgumentException ex) {
            return;
        }
        orderRepository.findById(id).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                transition.accept(order);
                orderRepository.save(order);
                domainEventPublisher.publish(order.pullDomainEvents());
            }
        });
    }

    private void publishOrderCreated(Order order) {
        if (!kafkaEnabled) {
            return;
        }
        List<OrderItemPayload> items = order.getItems().stream()
                .map(item -> new OrderItemPayload(item.getProductId().value(), item.getQuantity().value()))
                .toList();
        streamBridge.send("order.created",
                new OrderCreatedEvent(order.getId().value(), order.getUserId().value(), items, Instant.now()));
    }

    private void publishOrderCancelled(Order order) {
        if (!kafkaEnabled) {
            return;
        }
        List<OrderItemPayload> items = order.getItems().stream()
                .map(item -> new OrderItemPayload(item.getProductId().value(), item.getQuantity().value()))
                .toList();
        streamBridge.send("order.cancelled",
                new OrderCancelledEvent(order.getId().value(), items, Instant.now()));
    }

    private Order getOwnedOrThrow(String orderId, String userId) {
        Order order = findOrThrow(orderId);
        if (!order.getUserId().value().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "This order does not belong to you");
        }
        return order;
    }

    private Order findOrThrow(String orderId) {
        try {
            return orderRepository.findById(new OrderId(orderId))
                    .orElseThrow(OrderCommandService::orderNotFound);
        } catch (IllegalArgumentException ex) {
            throw orderNotFound();
        }
    }

    private static ApiException orderNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Order not found");
    }

    /** A missing, INACTIVE, or unreachable product is treated as unavailable, per O1's spec. */
    private ProductView fetchProduct(String productId) {
        try {
            String url = productBaseUrl + "/products/" + productId;
            return productRestTemplate.getForObject(url, ProductView.class);
        } catch (RestClientException ex) {
            throw stockInvariantViolation();
        }
    }

    /** A product not tracked by Inventory (404) or an unreachable Inventory Service is treated as unavailable. */
    private InventoryStockView fetchStock(String productId) {
        try {
            String url = inventoryBaseUrl + "/inventory/" + productId;
            return inventoryRestTemplate.getForObject(url, InventoryStockView.class);
        } catch (RestClientException ex) {
            throw stockInvariantViolation();
        }
    }

    private static ApiException stockInvariantViolation() {
        return new ApiException(HttpStatus.CONFLICT, "StockInvariantViolation",
                "One or more items are unavailable or have insufficient stock");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
