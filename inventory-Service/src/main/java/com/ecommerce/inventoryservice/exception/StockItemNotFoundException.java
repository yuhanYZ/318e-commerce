package com.ecommerce.inventoryservice.exception;

/** Thrown when I1 is queried for a productId with no StockItem record. */
public class StockItemNotFoundException extends RuntimeException {
    public StockItemNotFoundException(String message) {
        super(message);
    }
}
