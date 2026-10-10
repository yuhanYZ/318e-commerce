package com.ecommerce.orderservice.analytics;

import com.ecommerce.orderservice.domain.Money;
import com.ecommerce.orderservice.integration.OrderCreatedEvent;

import java.math.BigDecimal;

/**
 * The running value of one one-minute tumbling window of placed orders, as maintained inside the
 * Kafka Streams topology — the stream-side counterpart of the {@code order_analytics_window} row
 * (specs/iter2/3-domain-models-order-service-ddd.md §2.6.1).
 *
 * <p>Serialized into the aggregation's state store as JSON, so it stays a flat, Jackson-friendly
 * record.
 */
public record OrderAnalyticsBucket(long orderCount, BigDecimal totalAmount, String currency,
                                   long lastEventEpochMilli) {

    /** All orders share one aggregation group; the window supplies the bucketing. */
    public static final String SINGLE_GROUP = "ALL";

    public static OrderAnalyticsBucket empty() {
        return new OrderAnalyticsBucket(0L, BigDecimal.ZERO, null, 0L);
    }

    public OrderAnalyticsBucket plus(OrderCreatedEvent event) {
        Money amount = event.totalAmount();
        BigDecimal added = amount == null ? BigDecimal.ZERO : amount.amount();
        String resultingCurrency = currency != null ? currency : (amount == null ? null : amount.currency());
        long lastEvent = event.occurredAt() == null ? lastEventEpochMilli : event.occurredAt().toEpochMilli();
        return new OrderAnalyticsBucket(orderCount + 1, totalAmount.add(added), resultingCurrency, lastEvent);
    }
}
