package com.ecommerce.inventoryservice.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One line of an order in an Order Service integration event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemPayload(String productId, int quantity) {
}
