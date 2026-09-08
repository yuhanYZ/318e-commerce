package com.ecommerce.paymentservice.exception;

/**
 * Thrown for PAY1 when the referenced order either doesn't exist or isn't in
 * a payable state (PENDING). specs/openapi-contract.yaml maps BOTH cases to a
 * single 409 response ("Order not found or already paid") rather than a 404 -
 * followed literally here rather than "improving" it to a 404, since that's
 * what the contract actually specifies.
 */
public class OrderNotPayableException extends RuntimeException {
    public OrderNotPayableException(String message) {
        super(message);
    }
}
