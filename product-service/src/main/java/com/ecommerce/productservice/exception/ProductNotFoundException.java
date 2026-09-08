package com.ecommerce.productservice.exception;

/** Thrown by P2/P4 when a product id does not exist. Mapped to HTTP 404. */
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}
