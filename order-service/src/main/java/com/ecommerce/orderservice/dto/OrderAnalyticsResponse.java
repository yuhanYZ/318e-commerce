package com.ecommerce.orderservice.dto;

import java.time.Instant;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/OrderAnalyticsResponse. */
public record OrderAnalyticsResponse(
        int windowMinutes,
        Instant windowStart,
        Instant windowEnd,
        long orderCount,
        MoneyDto totalRevenue,
        MoneyDto averageOrderValue,
        Instant generatedAt) {
}
