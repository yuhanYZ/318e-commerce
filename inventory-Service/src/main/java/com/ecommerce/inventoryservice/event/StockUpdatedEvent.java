package com.ecommerce.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published after successfully deducting stock for an order item (I2 step
 * iii). No consumer exists yet in this repo - Product Service could
 * eventually cache stock levels from this instead of calling
 * GET /inventory/{productId} synchronously, but that isn't built - published
 * now so the contract exists once a consumer is added.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdatedEvent {
    private String productId;
    private int quantityAvailable;
}
