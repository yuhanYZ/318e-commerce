package com.ecommerce.orderservice.exception;

/**
 * Thrown when Product Service can't be reached to price an order item (O1).
 * Unlike a missing/optional field, a wrong or defaulted price is a business
 * correctness issue, so this fails the request rather than degrading silently.
 */
public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(String message) {
        super(message);
    }

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
