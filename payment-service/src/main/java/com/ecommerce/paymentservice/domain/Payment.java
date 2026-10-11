package com.ecommerce.paymentservice.domain;

import com.ecommerce.paymentservice.exception.ApiException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code PaymentAggregate} (root: Payment) —
 * specs/iter2/3-domain-models-payment-service-ddd.md §2.3.1.
 *
 * <p><b>Invariants</b>: (1) amount non-negative and equal to the linked order's total (the equality
 * is checked by the application service against Order Service before {@link #initiate});
 * (2) PENDING → SUCCESS | FAILED, and SUCCESS → REFUNDED only once the associated {@code Refund} is
 * processed — FAILED and REFUNDED are terminal; (3) initiation requires the order to be PENDING
 * (checked by the application service); (4) at most one non-FAILED payment per order (enforced by
 * the application service's pre-checks).
 */
@Entity
@Table(name = "payment")
public class Payment {

    /** The mock gateway is the only integration in this iteration. */
    public static final String METHOD_MOCK = "MOCK";

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id", nullable = false))
    private PaymentId id;

    @Convert(converter = OrderId.JpaConverter.class)
    @Column(name = "order_id", nullable = false)
    private OrderId orderId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false))
    })
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private String method;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected Payment() {
        // JPA
    }

    /** PAY1 — initiate a payment for a PENDING order. Starts in PENDING; emits {@code PaymentInitiated}. */
    public static Payment initiate(PaymentId id, OrderId orderId, Money amount, Instant now) {
        Payment payment = new Payment();
        payment.id = id;
        payment.orderId = orderId;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.method = METHOD_MOCK;
        payment.createdAt = now;
        payment.raise(new PaymentInitiated(id, orderId, amount, now));
        return payment;
    }

    /** Mock gateway outcome — success. Only from PENDING; emits {@code PaymentSucceeded}. */
    public PaymentSucceeded markSucceeded(Instant now) {
        requireStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.SUCCESS;
        PaymentSucceeded event = new PaymentSucceeded(id, orderId, now);
        raise(event);
        return event;
    }

    /** Mock gateway outcome — failure. Only from PENDING; emits {@code PaymentFailed}. */
    public PaymentFailed markFailed(Instant now) {
        requireStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.FAILED;
        PaymentFailed event = new PaymentFailed(id, orderId, now);
        raise(event);
        return event;
    }

    /**
     * Completes the refund: SUCCESS → REFUNDED, only from SUCCESS. Raises no event of its own —
     * the associated {@code RefundProcessed} is the auditable step (specification §2.3.1).
     */
    public void markRefunded() {
        requireStatus(PaymentStatus.SUCCESS);
        this.status = PaymentStatus.REFUNDED;
    }

    /** Drains the events raised by the behaviors above so the command service can dispatch them. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "PaymentStateViolation",
                    "Payment is not in " + expected + " status");
        }
    }

    private void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    public PaymentId getId() {
        return id;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getMethod() {
        return method;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
