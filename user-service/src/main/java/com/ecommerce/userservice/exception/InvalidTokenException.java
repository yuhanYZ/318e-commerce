package com.ecommerce.userservice.exception;

/** Thrown by U4 (confirm) when the reset token is missing, already used, or expired. Mapped to HTTP 400. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
