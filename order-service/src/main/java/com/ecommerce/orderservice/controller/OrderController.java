package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.request.CreateOrderRequest;
import com.ecommerce.orderservice.dto.response.OrderPageResponse;
import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presentation layer for the Order Service. Performs basic request/response
 * plumbing only - no business logic - per specs/1-technical-architecture.md.
 * Endpoint shapes follow specs/4-api-endpoints.md and specs/openapi-contract.yaml.
 * Every @RequestParam/@PathVariable below is explicitly named, even though the
 * parent pom now sets the -parameters compiler flag project-wide - defense in
 * depth against the exact bug that broke product-service's tests earlier.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** O1. */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request) {
        String userId = (String) authentication.getPrincipal();
        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** O3. */
    @GetMapping
    public ResponseEntity<OrderPageResponse> listMyOrders(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "0") int page) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(orderService.listMyOrders(userId, page));
    }

    /** O2. */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            Authentication authentication,
            @PathVariable(name = "id") String id) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(orderService.getOrderById(userId, id));
    }

    /** O4. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            Authentication authentication,
            @PathVariable(name = "id") String id) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(orderService.cancelOrder(userId, id));
    }
}
