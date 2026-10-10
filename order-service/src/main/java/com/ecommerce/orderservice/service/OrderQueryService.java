package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderAnalyticsService;
import com.ecommerce.orderservice.domain.OrderAnalyticsSummary;
import com.ecommerce.orderservice.domain.OrderAnalyticsWindow;
import com.ecommerce.orderservice.domain.OrderId;
import com.ecommerce.orderservice.domain.UserId;
import com.ecommerce.orderservice.dto.MoneyDto;
import com.ecommerce.orderservice.dto.OrderAnalyticsResponse;
import com.ecommerce.orderservice.dto.OrderPageResponse;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.exception.ApiException;
import com.ecommerce.orderservice.repository.OrderAnalyticsWindowRepository;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Query Application Service for Order Service — specs/iter2/6-cqrs-command-query.md §3.2.
 * Covers O2 (view status), O3 (view history) and O6 (real-time analytics). Reads only: the O6
 * buckets are read here and handed to the {@link OrderAnalyticsService} domain service, which does
 * the (pure) aggregation — this class never mutates the write model.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderAnalyticsWindowRepository analyticsRepository;
    private final OrderAnalyticsService analyticsService;
    private final int defaultWindowMinutes;

    public OrderQueryService(OrderRepository orderRepository,
                              OrderAnalyticsWindowRepository analyticsRepository,
                              OrderAnalyticsService analyticsService,
                              @Value("${app.analytics.window-minutes:15}") int defaultWindowMinutes) {
        this.orderRepository = orderRepository;
        this.analyticsRepository = analyticsRepository;
        this.analyticsService = analyticsService;
        this.defaultWindowMinutes = defaultWindowMinutes;
    }

    /** O2 — 404 if the order doesn't exist, 403 if it exists but isn't the caller's. */
    public OrderResponse getOrderById(String orderId, String userId) {
        Order order = findOrThrow(orderId);
        if (!order.getUserId().value().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "This order does not belong to you");
        }
        return OrderMapper.toResponse(order);
    }

    /** O3 — most-recent-first, paginated, empty list (not an error) if none. */
    public OrderPageResponse listMyOrders(String userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result = orderRepository.findByUserId(new UserId(userId), pageable);
        return new OrderPageResponse(result.getContent().stream().map(OrderMapper::toResponse).toList(),
                result.getNumber(), result.getTotalElements());
    }

    /** O6 — trailing-window analytics snapshot. Reads the projection, then delegates the aggregation. */
    public OrderAnalyticsResponse getRealtimeAnalytics(Integer requestedWindowMinutes) {
        int windowMinutes = requestedWindowMinutes != null ? requestedWindowMinutes : defaultWindowMinutes;
        if (windowMinutes < 1 || windowMinutes > 1440) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ValidationFailed",
                    "windowMinutes must be between 1 and 1440");
        }

        Instant generatedAt = Instant.now();
        Instant windowStart = generatedAt.minus(windowMinutes, ChronoUnit.MINUTES);
        List<OrderAnalyticsWindow> buckets =
                analyticsRepository.findByWindowStartGreaterThanEqualAndWindowStartLessThanEqual(
                        windowStart, generatedAt);
        OrderAnalyticsSummary summary = analyticsService.summarize(buckets, windowMinutes, generatedAt);

        return new OrderAnalyticsResponse(windowMinutes, windowStart, generatedAt, summary.orderCount(),
                new MoneyDto(summary.totalRevenue().amount(), summary.totalRevenue().currency()),
                new MoneyDto(summary.averageOrderValue().amount(), summary.averageOrderValue().currency()),
                generatedAt);
    }

    private Order findOrThrow(String orderId) {
        try {
            return orderRepository.findById(new OrderId(orderId)).orElseThrow(OrderQueryService::notFound);
        } catch (IllegalArgumentException ex) {
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Order not found");
    }
}
