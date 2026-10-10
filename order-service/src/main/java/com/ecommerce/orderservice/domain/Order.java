package com.ecommerce.orderservice.domain;

import com.ecommerce.orderservice.exception.ApiException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code OrderAggregate} (root: Order) — specs/iter2/3-domain-models-order-service-ddd.md §2.3.1.
 * Table named "orders", not "order" — ORDER is a reserved SQL keyword.
 *
 * <p><b>Invariants</b>: (1) {@code totalAmount} is computed here as the sum of
 * {@code unitPrice × quantity} over the items, each unit price being the Product Service price
 * snapshotted at placement time; (2) transitions are restricted to PENDING→PAID,
 * PENDING→PAYMENT_FAILED and {PENDING, PAID}→CANCELLED; (3) PAYMENT_FAILED and CANCELLED are
 * terminal; (4) shipping is out of scope, so there is no SHIPPED status.
 */
@Entity
@Table(name = "orders")
public class Order {

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id", nullable = false))
    private OrderId id;

    @Convert(converter = UserId.JpaConverter.class)
    @Column(name = "user_id", nullable = false)
    private UserId userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "total_currency", nullable = false))
    })
    private Money totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected Order() {
        // JPA
    }

    /** O1 — Place an Order. Computes {@code totalAmount} and emits {@code OrderPlaced}. */
    public static Order place(OrderId id, UserId userId, List<OrderItem> items, Instant now) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("an order must contain at least one item");
        }
        String currency = items.get(0).getUnitPrice().currency();
        Money total = items.stream()
                .map(item -> item.getUnitPrice().multiply(item.getQuantity()))
                .reduce(Money.zero(currency), Money::add);

        Order order = new Order();
        order.id = id;
        order.userId = userId;
        order.status = OrderStatus.PENDING;
        order.totalAmount = total;
        order.createdAt = now;
        order.updatedAt = now;
        items.forEach(order::addItem);
        order.raise(new OrderPlaced(id, userId, total, now));
        return order;
    }

    /**
     * O5 — PaymentConfirmedEvent branch. Only legal from PENDING (invariant 2); the Kafka consumer
     * in {@code OrderCommandService} checks the status first so a redelivered message stays a
     * silent no-op rather than a 409.
     */
    public OrderPaid markPaid(Instant now) {
        requireStatus(OrderStatus.PENDING);
        this.status = OrderStatus.PAID;
        this.updatedAt = now;
        OrderPaid event = new OrderPaid(id, now);
        raise(event);
        return event;
    }

    /** O5 — PaymentFailedEvent branch. Only legal from PENDING (invariant 2). */
    public OrderPaymentFailed markPaymentFailed(Instant now) {
        requireStatus(OrderStatus.PENDING);
        this.status = OrderStatus.PAYMENT_FAILED;
        this.updatedAt = now;
        OrderPaymentFailed event = new OrderPaymentFailed(id, now);
        raise(event);
        return event;
    }

    /** O4 — Cancel an Order. Only PENDING/PAID can be cancelled; both terminal states reject with 409. */
    public OrderCancelled cancel(Instant now) {
        if (status != OrderStatus.PENDING && status != OrderStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "OrderStateViolation",
                    "Order cannot be cancelled from its current status");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = now;
        OrderCancelled event = new OrderCancelled(id, now);
        raise(event);
        return event;
    }

    /** Drains the events raised by the behaviors above so the command service can dispatch them. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "OrderStateViolation",
                    "Order is not in " + expected + " status");
        }
    }

    private void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    public OrderId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
