package com.ecommerce.orderservice.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shape of the response from Inventory Service's GET /inventory/{productId}
 * (specs/openapi-contract.yaml StockResponse schema). Used only for deserializing
 * that inter-service call in InventoryClient - never returned directly by this
 * service's own API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockResponse {
    private String productId;
    private int quantityAvailable;
    private boolean isAvailable;
}
