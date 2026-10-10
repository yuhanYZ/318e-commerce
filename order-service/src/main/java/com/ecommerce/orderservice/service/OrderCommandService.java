package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderItem;
import com.ecommerce.orderservice.domain.OrderStatus;
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
 * Command Application Service for Order Service — specs/iter2/6-cqrs-command-query.md §3.
 * Covers O1 (place), O4 (cancel), and O5 (consumes PaymentConfirmedEvent/PaymentFailedEvent —
 * {@link #updateOrderOnPaymentConfirmed}/{@link #updateOrderOnPaymentFailed}, invoked by the
 * {@code Consumer<T>} beans in {@code config.KafkaConsumerConfig}).
 *
 * <p>O1 validates every item (product exists + ACTIVE, sufficient stock) BEFORE creating/saving
 * the Order — the whole per-item loop runs first, and {@code orderRepository.save} only happens
 * once every item has passed. Same validate-then-write pattern as inventory-Service/product-
 * service, so no {@code noRollbackFor} override is needed here either (see Bug #3 lesson).
 *
 * <p><b>Kafka publishing (O1/O4) is gated by {@code app.kafka.enabled} (default {@code false}).</b>
 * It is only set {@code true} under the "kafka" Spring profile (see application.yml), which no
 * contract test ever activates — so {@link #publishOrderCreated}/{@link #publishOrderCancelled}
 * are guaranteed no-ops during {@code mvn test}, and {@code placeOrder}/{@code cancel}'s existing,
 * already-verified behavior is unchanged for the test suite.
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
    private final OrderAnalyticsService analyticsService;

    public OrderCommandService(OrderRepository orderRepository,
                                @Qualifier("productRestTemplate") RestTemplate productRestTemplate,
                                @Qualifier("inventoryRestTemplate") RestTemplate inventoryRestTemplate,
                                @Value("${app.product-service.base-url}") String productBaseUrl,
                                @Value("${app.inventory-service.base-url}") String inventoryBaseUrl,
                                StreamBridge streamBridge,
                                @Value("${app.kafka.enabled:false}") boolean kafkaEnabled,
                                OrderAnalyticsService analyticsService) {
        this.orderRepository = orderRepository;
        this.productRestTemplate = productRestTemplate;
        this.inventoryRestTemplate = inventoryRestTemplate;
        this.productBaseUrl = productBaseUrl;
        this.inventoryBaseUrl = inventoryBaseUrl;
        this.streamBridge = streamBridge;
        this.kafkaEnabled = kafkaEnabled;
        this.analyticsService = analyticsService;
    }

    /** O1 — Place an Order. */
    public OrderResponse placeOrder(String userId, CreateOrderRequest request) {
        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        String currency = null;

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
            if (currency == null) {
                currency = itemCurrency;
            }
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity())));
            items.add(new OrderItem(UUID.randomUUID().toString(), itemReq.productId(), itemReq.quantity(),
                    unitPrice, itemCurrency));
        }

        Order order = new Order("ORD-" + shortId());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(total);
        order.setTotalCurrency(currency);
        Instant now = Instant.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        items.forEach(order::addItem);

        orderRepository.save(order);
        analyticsService.recordOrderPlaced(order, now);
        publishOrderCreated(order);
        return OrderMapper.toResponse(order);
    }

    /** O4 — Cancel an Order. Only PENDING/PAID can be cancelled; both terminal states reject with 409. */
    public OrderResponse cancel(String orderId, String userId) {
        Order order = getOwnedOrThrow(orderId, userId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "OrderStateViolation",
                    "Order cannot be cancelled from its current status");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        publishOrderCancelled(order);
        return OrderMapper.toResponse(order);
    }

    /**
     * O5 — Update Order Status on Payment Event (PaymentConfirmedEvent branch). Idempotent on
     * orderId: only transitions an order that is still PENDING. A missing order, or one that has
     * already moved on (redelivered Kafka message, or a race with a cancel), is a silent no-op —
     * a Kafka consumer must never throw on a message that's simply already handled or irrelevant.
     */
    public void updateOrderOnPaymentConfirmed(String orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PAID);
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
            }
        });
    }

    /** O5 — Update Order Status on Payment Event (PaymentFailedEvent branch). Same idempotency rule as above. */
    public void updateOrderOnPaymentFailed(String orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PAYMENT_FAILED);
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
            }
        });
    }

    private void publishOrderCreated(Order order) {
        if (!kafkaEnabled) {
            return;
        }
        List<OrderItemPayload> items = order.getItems().stream()
                .map(item -> new OrderItemPayload(item.getProductId(), item.getQuantity()))
                .toList();
        streamBridge.send("order.created",
                new OrderCreatedEvent(order.getId(), order.getUserId(), items, Instant.now()));
    }

    private void publishOrderCancelled(Order order) {
        if (!kafkaEnabled) {
            return;
        }
        List<OrderItemPayload> items = order.getItems().stream()
                .map(item -> new OrderItemPayload(item.getProductId(), item.getQuantity()))
                .toList();
        streamBridge.send("order.cancelled", new OrderCancelledEvent(order.getId(), items, Instant.now()));
    }

    private Order getOwnedOrThrow(String orderId, String userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "This order does not belong to you");
        }
        return order;
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
