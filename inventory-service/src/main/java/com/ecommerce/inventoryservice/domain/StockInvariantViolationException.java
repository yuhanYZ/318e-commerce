package com.ecommerce.inventoryservice.domain;

/**
 * A {@code StockItem} invariant was violated: stock would go below zero, or stock was deducted or
 * released for a product that has no event stream (domain-models §2.3.1, invariants 1-3).
 *
 * <p>A domain exception with no web dependency. The REST layer maps it to HTTP 409
 * {@code StockInvariantViolation}; the Kafka handlers catch and log it instead, since the spec
 * says a rejected deduction is logged, and rethrowing would cause endless redelivery.
 */
public class StockInvariantViolationException extends RuntimeException {

    public StockInvariantViolationException(String message) {
        super(message);
    }
}
