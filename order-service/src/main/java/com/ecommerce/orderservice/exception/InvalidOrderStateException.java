package com.ecommerce.orderservice.exception;

/** Thrown when an order state transition isn't allowed, e.g. cancelling an already-SHIPPED/CANCELLED order (O4). */
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
