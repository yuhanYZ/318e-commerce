package com.ecommerce.productservice.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shape of the response from Inventory Service's GET /inventory/{productId}
 * (specs/openapi-contract.yaml StockResponse schema). Used only for deserializing
 * that inter-service call in InventoryClient - never returned directly by this
 * service's own API.
 * <p>
 * The boolean field is deliberately pinned to the wire name "isAvailable" via
 * {@code @JsonProperty}, mirroring the same fix on the producer side
 * (inventory-service's own StockResponse) and on order-service's identical
 * copy of this DTO. Without it, Lombok's generated setter for a field
 * literally named {@code isAvailable} is {@code setAvailable(boolean)}
 * (Lombok strips the leading "is" once when deriving accessor names for a
 * boolean field that already starts with "is"), so Jackson looks for a JSON
 * property called "available" - not "isAvailable" - to populate it. Since the
 * real payload's key is "isAvailable", that lookup silently misses (Spring
 * Boot's default ObjectMapper does not fail on unmapped fields) and this
 * field is left at its default value of {@code false}, making every stock
 * check look unavailable regardless of the real quantity. This was invisible
 * in every existing test because they all construct StockResponse directly
 * via its constructor, never through actual JSON deserialization - only a
 * real, live call to Inventory Service exercises this path.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockResponse {
    private String productId;
    private int quantityAvailable;

    @JsonProperty("isAvailable")
    private boolean isAvailable;
}
