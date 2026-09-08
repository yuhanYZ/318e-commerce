package com.ecommerce.orderservice.exception;

/** Thrown when the authenticated user requests an order that exists but belongs to someone else (O2). */
public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
