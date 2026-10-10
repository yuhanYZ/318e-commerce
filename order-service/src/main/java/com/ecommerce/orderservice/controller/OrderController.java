package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderAnalyticsResponse;
import com.ecommerce.orderservice.dto.OrderPageResponse;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.service.OrderAnalyticsService;
import com.ecommerce.orderservice.service.OrderCommandService;
import com.ecommerce.orderservice.service.OrderQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** specs/iter2/openapi-contract-v2.yaml paths /orders/*. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderCommandService commandService;
    private final OrderQueryService queryService;
    private final OrderAnalyticsService analyticsService;

    public OrderController(OrderCommandService commandService, OrderQueryService queryService,
                           OrderAnalyticsService analyticsService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.analyticsService = analyticsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        return commandService.placeOrder(authentication.getName(), request);
    }

    @GetMapping
    public OrderPageResponse listMyOrders(Authentication authentication,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return queryService.listMyOrders(authentication.getName(), page, size);
    }

    @GetMapping("/analytics/realtime")
    public OrderAnalyticsResponse getRealtimeOrderAnalytics(
            @RequestParam(required = false) Integer windowMinutes) {
        return analyticsService.getRealtimeAnalytics(windowMinutes);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable String id, Authentication authentication) {
        return queryService.getOrderById(id, authentication.getName());
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable String id, Authentication authentication) {
        return commandService.cancel(id, authentication.getName());
    }
}
