package com.ecommerce.userservice.exception;

/** Thrown by U2 when email/password do not match, the account does not exist, or is deactivated. Mapped to HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
