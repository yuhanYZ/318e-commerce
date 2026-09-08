package com.ecommerce.inventoryservice.exception;

import com.ecommerce.inventoryservice.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Translates service-layer exceptions and validation failures into the
 * ErrorResponse shape defined in specs/openapi-contract.yaml (code, message,
 * timestamp), mirroring order-service's GlobalExceptionHandler. The catch-all
 * handler logs every unhandled exception at ERROR from the start - product
 * -service's original silent catch-all is what made its earlier test failures
 * so hard to diagnose.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(StockItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStockItemNotFound(StockItemNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "STOCK_ITEM_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidStockAdjustmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStockAdjustment(InvalidStockAdjustmentException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_STOCK_ADJUSTMENT", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Validation failed";
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        ErrorResponse body = new ErrorResponse(code, message, Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
