package com.ecommerce.orderservice.domain;

/**
 * specs/iter2/3-domain-models-order-service-ddd.md §2.2.6. No SHIPPED status (Yuhan's decision,
 * see project decisions log) — PAYMENT_FAILED and CANCELLED are the terminal states.
 * Allowed transitions: PENDING->PAID, PENDING->PAYMENT_FAILED, {PENDING,PAID}->CANCELLED.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    PAYMENT_FAILED,
    CANCELLED
}
