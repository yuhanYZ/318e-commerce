package com.ecommerce.userservice.exception;

/** Thrown by U1 when the submitted email is already registered. Mapped to HTTP 409. */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
