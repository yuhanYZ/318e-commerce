package com.ecommerce.inventoryservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-level failure carrying the HTTP status and the {@code code} from the error
 * table in 4-api-endpoints-v2.md §8.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", message);
    }

    public static ApiException validationFailed(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ValidationFailed", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
