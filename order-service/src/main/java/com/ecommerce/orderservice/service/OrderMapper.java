package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderItem;
import com.ecommerce.orderservice.dto.MoneyDto;
import com.ecommerce.orderservice.dto.OrderItemResponse;
import com.ecommerce.orderservice.dto.OrderResponse;

import java.util.List;

/** Maps the aggregate onto the API contract's DTOs — specs/iter2/openapi-contract-v2.yaml. */
final class OrderMapper {

    private OrderMapper() {
    }

    static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderMapper::toItemResponse)
                .toList();
        return new OrderResponse(order.getId().value(), order.getUserId().value(), order.getStatus().name(),
                new MoneyDto(order.getTotalAmount().amount(), order.getTotalAmount().currency()), items,
                order.getCreatedAt());
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getProductId().value(), item.getQuantity().value(),
                new MoneyDto(item.getUnitPrice().amount(), item.getUnitPrice().currency()));
    }
}
