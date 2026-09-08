package com.ecommerce.paymentservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulates a payment gateway - there is no real or sandbox gateway to
 * integrate with for this project. The outcome is deterministic rather than
 * random, the way real sandbox gateways work: Stripe and PayPal's test modes
 * use reserved card numbers to trigger a specific decline, rather than a
 * random failure rate, precisely so client code (and its tests) can exercise
 * both branches reliably. Here, submitting {@code app.payment.mock-decline
 * -trigger} as {@link com.ecommerce.paymentservice.dto.request.PaymentRequest#getMethod()}
 * simulates a decline; any other value simulates a successful authorization.
 */
@Component
public class MockPaymentGateway {

    private final String declineTrigger;

    public MockPaymentGateway(@Value("${app.payment.mock-decline-trigger}") String declineTrigger) {
        this.declineTrigger = declineTrigger;
    }

    /** @return true if the simulated gateway authorizes the payment, false if it declines. */
    public boolean authorize(String method) {
        return !declineTrigger.equalsIgnoreCase(method);
    }
}
