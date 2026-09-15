package com.ecommerce.inventoryservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data access to the {@code stock_event} table. Use {@link StockEventStore}, not this, from services. */
public interface StockEventRepository extends JpaRepository<StockEventEntity, String> {

    List<StockEventEntity> findByAggregateIdOrderByAggregateVersionAsc(String aggregateId);

    List<StockEventEntity> findByOrderIdAndEventTypeOrderByOccurredAtAsc(String orderId, String eventType);
}
