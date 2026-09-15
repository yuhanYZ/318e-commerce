package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderItem;
import com.ecommerce.orderservice.dto.MoneyDto;
import com.ecommerce.orderservice.dto.OrderItemResponse;
import com.ecommerce.orderservice.dto.OrderResponse;

import java.util.List;

final class OrderMapper {

    private OrderMapper() {
    }

    static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderMapper::toItemResponse)
                .toList();
        return new OrderResponse(order.getId(), order.getUserId(), order.getStatus().name(),
                new MoneyDto(order.getTotalAmount(), order.getTotalCurrency()), items, order.getCreatedAt());
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getQuantity(),
                new MoneyDto(item.getUnitPriceAmount(), item.getUnitPriceCurrency()));
    }
}
