package com.ecommerce.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Published on the "stock-reservation-failed" topic when a race condition
 * leaves insufficient stock for one or more items in an already-created order
 * (I2 step iii): "供 Order Service 据此回滚订单" (specs/2-user-stories.md User
 * Story I2). order-service has no consumer for this yet - a known gap, since
 * order-service currently only checks stock synchronously via
 * GET /inventory/{productId} before creating the order (O1), so this event
 * only matters for the race-condition window between that check and this
 * service's Kafka consumption of OrderCreatedEvent. Flagged for the user as
 * follow-up work, not fixed here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationFailedEvent {
    private String orderId;
    private List<String> insufficientProductIds;
}
