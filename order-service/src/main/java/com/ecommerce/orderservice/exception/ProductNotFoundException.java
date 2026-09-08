package com.ecommerce.orderservice.exception;

/** Thrown when Product Service reports 404 for a productId referenced in an order request (O1). */
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}
