package com.ecommerce.paymentservice.domain;

/** specs/iter2/3-domain-models-payment-service-ddd.md §2.2.4. FAILED and REFUNDED are terminal. */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
