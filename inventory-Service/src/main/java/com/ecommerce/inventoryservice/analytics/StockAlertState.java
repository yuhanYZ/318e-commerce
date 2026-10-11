package com.ecommerce.inventoryservice.analytics;

/**
 * What the I4 topology emits for every product update: the latest quantity plus the threshold
 * verdict. Deciding the verdict is the topology's job; turning it into the open/resolve lifecycle
 * is the sink's.
 */
public record StockAlertState(String productId, int quantityAvailable, boolean belowThreshold,
                              long occurredAtEpochMilli) {

    public static StockAlertState of(ProductStock stock, int lowStockThreshold) {
        return new StockAlertState(stock.productId(), stock.quantityAvailable(),
                stock.quantityAvailable() < lowStockThreshold, stock.lastEventEpochMilli());
    }
}
