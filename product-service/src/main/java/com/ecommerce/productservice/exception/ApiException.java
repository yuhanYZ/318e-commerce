package com.ecommerce.productservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Single exception type carrying an HTTP status + the contract's error `code`
 * (specs/iter2/4-api-endpoints-v2.md §8). Kept as one class rather than a hierarchy to avoid
 * a proliferation of near-identical exception classes across six independent modules.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
