package com.ecommerce.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Matches the OrderPageResponse schema in specs/openapi-contract.yaml. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPageResponse {
    private List<OrderResponse> content;
    private int page;
    private long totalElements;
}
