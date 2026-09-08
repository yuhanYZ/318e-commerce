package com.ecommerce.paymentservice.exception;

/** Thrown for PAY2 (no Payment exists yet for the given orderId) and for an unknown payment id in PAY3. */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
