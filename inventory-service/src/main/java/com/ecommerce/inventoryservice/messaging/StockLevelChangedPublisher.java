package com.ecommerce.inventoryservice.messaging;

/**
 * Hands a stock level change to the I4 low-stock stream function.
 *
 * <p>Two implementations, selected by profile, so the command side never knows which is active:
 * <ul>
 *   <li>{@link InProcessStockLevelChangedPublisher} (default): calls the function directly, inside
 *       the command's transaction, so stock and alerts commit or roll back together.</li>
 *   <li>{@link StreamBridgeStockLevelChangedPublisher} ({@code kafka}): sends to the internal topic
 *       after commit, where Spring Cloud Stream delivers it to the same function.</li>
 * </ul>
 */
public interface StockLevelChangedPublisher {

    void publish(StockLevelChanged change);
}
