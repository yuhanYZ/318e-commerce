package com.ecommerce.orderservice.domain;

/**
 * Lifecycle status of an {@link Order}, per specs/3-domain-models.md section 4.1.
 * <ul>
 *   <li>{@code PENDING} - created, awaiting payment (O1).</li>
 *   <li>{@code PAID} - payment confirmed via {@code PaymentConfirmedEvent} (O5).</li>
 *   <li>{@code PAYMENT_FAILED} - payment failed via {@code PaymentFailedEvent} (O5).</li>
 *   <li>{@code SHIPPED} - dispatched. No shipping workflow exists yet in this
 *       codebase; reserved for a future fulfilment step, per the "OrderStatus
 *       state machine to be completed" open question in specs/2-user-stories.md.</li>
 *   <li>{@code CANCELLED} - cancelled by the customer before shipping (O4).</li>
 * </ul>
 */
public enum OrderStatus {
    PENDING,
    PAID,
    PAYMENT_FAILED,
    SHIPPED,
    CANCELLED
}
