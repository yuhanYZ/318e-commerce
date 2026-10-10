package com.ecommerce.paymentservice.domain;

import com.ecommerce.paymentservice.exception.ApiException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code RefundAggregate} (root: Refund) —
 * specs/iter2/3-domain-models-payment-service-ddd.md §2.3.2.
 *
 * <p><b>Invariants</b>: (1) the refund amount equals the original payment amount — full refunds
 * only; (2) at most one active (non-REJECTED) refund per payment; (3) a refund is only requested
 * when the payment is SUCCESS and the linked order is CANCELLED (checked by the application
 * service); (4) with the mock gateway a requested refund is always processed immediately, so
 * REQUESTED → PROCESSED happens within one command and {@code reject} is never called.
 */
@Entity
@Table(name = "refund")
public class Refund {

    @Id
    private String id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "payment_id", nullable = false))
    private PaymentId paymentId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false))
    })
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected Refund() {
        // JPA
    }

    /** PAY3 — request a full refund. Emits {@code RefundRequested}. */
    public static Refund request(String id, PaymentId paymentId, Money amount, Instant now) {
        Refund refund = new Refund();
        refund.id = id;
        refund.paymentId = paymentId;
        refund.amount = amount;
        refund.status = RefundStatus.REQUESTED;
        refund.requestedAt = now;
        refund.raise(new RefundRequested(id, paymentId, amount, now));
        return refund;
    }

    /** Only from REQUESTED; emits {@code RefundProcessed} and stamps {@code processedAt}. */
    public RefundProcessed process(Instant now) {
        requireStatus(RefundStatus.REQUESTED);
        this.status = RefundStatus.PROCESSED;
        this.processedAt = now;
        RefundProcessed event = new RefundProcessed(id, paymentId, now);
        raise(event);
        return event;
    }

    /** Reserved for a future real gateway — never called while the mock gateway always succeeds. */
    public RefundRejected reject(Instant now) {
        requireStatus(RefundStatus.REQUESTED);
        this.status = RefundStatus.REJECTED;
        this.processedAt = now;
        RefundRejected event = new RefundRejected(id, paymentId, now);
        raise(event);
        return event;
    }

    /** Drains the events raised by the behaviors above so the command service can dispatch them. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void requireStatus(RefundStatus expected) {
        if (status != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "PaymentStateViolation",
                    "Refund is not in " + expected + " status");
        }
    }

    private void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    public String getId() {
        return id;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public Money getAmount() {
        return amount;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
