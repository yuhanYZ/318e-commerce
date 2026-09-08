package com.ecommerce.paymentservice.exception;

/**
 * Thrown when Order Service can't be reached to validate ownership, order
 * status, or total amount (PAY1-PAY3). Every one of those checks is a
 * correctness/security requirement, not a nicety, so an unreachable Order
 * Service fails the request with 503 rather than degrading gracefully -
 * mirroring order-service's own ProductClient/ProductServiceUnavailableException
 * stance for the same reason.
 */
public class OrderServiceUnavailableException extends RuntimeException {
    public OrderServiceUnavailableException(String message) {
        super(message);
    }

    public OrderServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
