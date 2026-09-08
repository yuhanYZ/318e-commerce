package com.ecommerce.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Published on the "refund-processed" topic after PAY3 successfully processes
 * a refund (User Story PAY3 step iii). No consumer exists yet anywhere in
 * this repo - a known gap, published now so the contract exists once a
 * consumer is added (e.g. a future notification service, or Order Service
 * tracking refund history on the order itself).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundProcessedEvent {
    private String orderId;
    private String paymentId;
    private String refundId;
    private BigDecimal amount;
}
