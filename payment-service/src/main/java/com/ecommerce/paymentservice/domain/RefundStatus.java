package com.ecommerce.paymentservice.domain;

/**
 * specs/iter2/3-domain-models-payment-service-ddd.md §2.2.5. With the mock gateway a refund is
 * always processed immediately — REJECTED is reserved for a future real-gateway integration and
 * is never produced in this iteration.
 */
public enum RefundStatus {
    REQUESTED,
    PROCESSED,
    REJECTED
}
