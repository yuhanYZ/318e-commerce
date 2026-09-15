package com.ecommerce.productservice.exception;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps every failure onto the ErrorResponse contract in 4-api-endpoints-v2.md §8.
 *
 * <p>401 and 403 are not handled here: those are raised inside the security filter chain,
 * before a controller is ever selected, so they are rendered by RestAuthenticationEntryPoint
 * and RestAccessDeniedHandler instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    /** Invariant 2: an INACTIVE product is terminal - 409 ProductStateViolation. */
    @ExceptionHandler(Product.InactiveProductException.class)
    public ResponseEntity<ErrorResponse> handleInactiveProduct(Product.InactiveProductException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ProductStateViolation", "An INACTIVE product cannot be updated"));
    }

    /** Bean-validation failures on a request body, reported field by field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("ValidationFailed", "Request validation failed", fieldErrors));
    }

    /** A required query parameter is absent, for example /products/search with no q. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("ValidationFailed", "Required parameter is missing: " + ex.getParameterName()));
    }

    /** A query parameter of the wrong type, for example page=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("ValidationFailed", "Invalid value for parameter: " + ex.getName()));
    }

    /** Malformed or unparseable JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("ValidationFailed", "Malformed request body"));
    }

    /**
     * Domain invariants raise IllegalArgumentException from value objects such as Money.
     * Bean validation normally catches these first; this is the backstop that keeps an
     * invariant violation from surfacing as a 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("ValidationFailed", ex.getMessage()));
    }
}
