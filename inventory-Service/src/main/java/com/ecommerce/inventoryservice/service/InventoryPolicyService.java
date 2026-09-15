package com.ecommerce.inventoryservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Domain Service — specs/iter2/3-domain-models-inventory-service-ddd.md §2.4.1. Compares
 * available stock against the single global low-stock threshold configuration
 * ({@code app.inventory.low-stock-threshold}, default 5).
 */
@Component
public class InventoryPolicyService {

    private final int lowStockThreshold;

    public InventoryPolicyService(@Value("${app.inventory.low-stock-threshold}") int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isBelowThreshold(int quantityAvailable) {
        return quantityAvailable < lowStockThreshold;
    }
}
