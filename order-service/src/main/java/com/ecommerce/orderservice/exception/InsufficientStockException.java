package com.ecommerce.orderservice.exception;

/** Thrown when Inventory Service reports insufficient stock for one or more requested items (O1). */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
