package com.ecommerce.inventoryservice.exception;

/** Thrown when an I3 adjustment would make quantityAvailable negative (User Story I3 step ii). */
public class InvalidStockAdjustmentException extends RuntimeException {
    public InvalidStockAdjustmentException(String message) {
        super(message);
    }
}
