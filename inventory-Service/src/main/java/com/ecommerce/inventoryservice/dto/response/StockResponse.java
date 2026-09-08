package com.ecommerce.inventoryservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for I1/I3 (specs/openapi-contract.yaml StockResponse schema).
 * The boolean field is deliberately named {@code available} (not
 * {@code isAvailable}) and remapped to the wire name "isAvailable" via
 * {@code @JsonProperty}: Lombok's getter for a field already named
 * "isAvailable" would be {@code isAvailable()}, and Jackson's default bean
 * -property naming strips that "is" prefix again when deriving the JSON key,
 * which would silently serialize this as "available" instead of "isAvailable"
 * and break the openapi contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockResponse {
    private String productId;
    private int quantityAvailable;

    @JsonProperty("isAvailable")
    private boolean available;
}
