package com.ecommerce.inventoryservice.analytics;

/**
 * Where the I4 topology hands each product's threshold verdict. A separate seam so the topology can
 * be driven by {@code TopologyTestDriver} with a recording sink — no broker, no Spring context.
 */
@FunctionalInterface
public interface LowStockAlertSink {

    void apply(StockAlertState state);
}
