package com.ecommerce.inventoryservice.domain;

/**
 * {@code InventoryPolicyService} domain service - domain-models §2.4.1.
 *
 * <p>Holds the single global low-stock threshold ({@code app.inventory.low-stock-threshold},
 * default 5). Plain Java: the threshold is injected by configuration, so the domain layer carries
 * no Spring dependency.
 *
 * <p>This is the only policy check the spec defines. An alert opens when it returns true and
 * resolves when it returns false, so there is no gap at exactly the threshold.
 */
public class InventoryPolicyService {

    private final int lowStockThreshold;

    public InventoryPolicyService(int lowStockThreshold) {
        if (lowStockThreshold < 0) {
            throw new IllegalArgumentException("low-stock threshold must not be negative: " + lowStockThreshold);
        }
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isBelowThreshold(Quantity quantityAvailable) {
        return quantityAvailable.value() < lowStockThreshold;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }
}
