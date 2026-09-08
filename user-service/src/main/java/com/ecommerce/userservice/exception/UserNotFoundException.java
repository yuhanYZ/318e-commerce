package com.ecommerce.userservice.exception;

/** Thrown when a user referenced by an authenticated request id can no longer be found. Mapped to HTTP 404. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
