package com.ecommerce.paymentservice.exception;

/**
 * Thrown when the authenticated user tries to pay for, view the payment of,
 * or refund an order that belongs to someone else. Not explicitly called out
 * in specs/openapi-contract.yaml (only 201/409/404/403-for-Admin-routes are
 * documented per endpoint) - added as a defense-in-depth interpretation gap
 * -fill, mirroring order-service's own ownership model for O2/O4.
 */
public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
