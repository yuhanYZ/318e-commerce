package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.dto.OrderPageResponse;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.exception.ApiException;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query Application Service for Order Service — specs/iter2/6-cqrs-command-query.md §3.
 * Covers O2 (view status) and O3 (view history).
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** O2 — 404 if the order doesn't exist, 403 if it exists but isn't the caller's. */
    public OrderResponse getOrderById(String orderId, String userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "This order does not belong to you");
        }
        return OrderMapper.toResponse(order);
    }

    /** O3 — most-recent-first, paginated, empty list (not an error) if none. */
    public OrderPageResponse listMyOrders(String userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result = orderRepository.findByUserId(userId, pageable);
        return new OrderPageResponse(result.getContent().stream().map(OrderMapper::toResponse).toList(),
                result.getNumber(), result.getTotalElements());
    }
}
