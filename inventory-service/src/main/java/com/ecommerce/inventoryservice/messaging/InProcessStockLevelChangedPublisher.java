package com.ecommerce.inventoryservice.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Default publisher: invokes the {@code lowStockAlertJob} stream function bean directly.
 *
 * <p>Runs synchronously in the caller's transaction, so there is no broker to run in tests or local
 * development, and a stock change and the alert it causes are atomic.
 */
@Component
@Profile("!kafka")
public class InProcessStockLevelChangedPublisher implements StockLevelChangedPublisher {

    private final Consumer<StockLevelChanged> lowStockAlertJob;

    public InProcessStockLevelChangedPublisher(Consumer<StockLevelChanged> lowStockAlertJob) {
        this.lowStockAlertJob = lowStockAlertJob;
    }

    @Override
    public void publish(StockLevelChanged change) {
        lowStockAlertJob.accept(change);
    }
}
