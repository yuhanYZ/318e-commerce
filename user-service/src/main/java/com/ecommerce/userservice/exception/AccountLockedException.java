package com.ecommerce.userservice.exception;

/** Thrown by U2 when the account is currently locked after too many failed attempts. Mapped to HTTP 423. */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
