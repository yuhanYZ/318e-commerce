package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.StockEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockEventRepository extends JpaRepository<StockEvent, String> {

    List<StockEvent> findByAggregateIdOrderByAggregateVersionAsc(String aggregateId);

    /** I2/I5 idempotency guard: has this (productId, orderId) pair already produced this event type? */
    boolean existsByAggregateIdAndOrderIdAndEventType(String aggregateId, String orderId, String eventType);

    /**
     * I5 (PaymentFailedEvent branch): PaymentFailedEvent carries only an orderId, no item list, so
     * this reconstructs "what was deducted for this order" by scanning the event store for every
     * StockDeducted event tagged with that orderId, across all productIds.
     */
    List<StockEvent> findByOrderIdAndEventType(String orderId, String eventType);
}
