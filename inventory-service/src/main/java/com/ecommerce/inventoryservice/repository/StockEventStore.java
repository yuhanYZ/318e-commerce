package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.StockItem;
import com.ecommerce.inventoryservice.domain.event.StockAdjusted;
import com.ecommerce.inventoryservice.domain.event.StockDeducted;
import com.ecommerce.inventoryservice.domain.event.StockItemEvent;
import com.ecommerce.inventoryservice.domain.event.StockReleased;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Event store for {@code StockItemAggregate} - 5-event-sourcing.md §3.4.
 *
 * <p>Infrastructure: translates between domain events and {@code stock_event} rows, so the aggregate
 * never sees JSON or JPA.
 *
 * <p>Idempotency: events raised by the Kafka handlers (deductions and releases) get a
 * deterministic event id derived from {@code (orderId, productId, eventType)}. A redelivered message
 * therefore maps to an id that already exists, and callers check for it before acting. Manual
 * adjustments are not message-driven and get a random id.
 */
@Component
public class StockEventStore {

    public static final String AGGREGATE_TYPE = "StockItemAggregate";

    private static final Map<String, Class<? extends StockItemEvent>> EVENT_TYPES = Map.of(
            StockAdjusted.TYPE, StockAdjusted.class,
            StockDeducted.TYPE, StockDeducted.class,
            StockReleased.TYPE, StockReleased.class);

    private final StockEventRepository repository;
    private final ObjectMapper objectMapper;

    public StockEventStore(StockEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Rehydrates the aggregate from its full stream. A product with no events comes back untracked. */
    public StockItem load(String productId) {
        List<StockItemEvent> history = repository.findByAggregateIdOrderByAggregateVersionAsc(productId).stream()
                .map(this::toDomainEvent)
                .toList();
        return StockItem.rehydrate(productId, history);
    }

    /**
     * Appends the aggregate's pending events with consecutive versions and flushes immediately, so a
     * version clash with a concurrent writer surfaces here, inside the caller's retry loop, rather than
     * later at commit.
     *
     * @return the events that were appended
     */
    public List<StockItemEvent> append(StockItem item) {
        long version = item.firstPendingVersion();
        List<StockItemEvent> pending = item.pullPendingEvents();

        List<StockEventEntity> rows = new java.util.ArrayList<>(pending.size());
        for (StockItemEvent event : pending) {
            rows.add(new StockEventEntity(eventIdFor(event), event.productId(), AGGREGATE_TYPE, event.eventType(),
                    version++, event.occurredAt(), toJson(event), orderIdOf(event)));
        }
        repository.saveAllAndFlush(rows);
        return pending;
    }

    public boolean exists(String eventId) {
        return repository.existsById(eventId);
    }

    /** The deduction made for one order line, if any. Looked up by its deterministic id. */
    public Optional<StockDeducted> findDeduction(String orderId, String productId) {
        return repository.findById(deterministicEventId(orderId, productId, StockDeducted.TYPE))
                .map(row -> (StockDeducted) toDomainEvent(row));
    }

    /** Every deduction recorded for an order, across all products. Used for failed-payment releases. */
    public List<StockDeducted> findDeductionsForOrder(String orderId) {
        return repository.findByOrderIdAndEventTypeOrderByOccurredAtAsc(orderId, StockDeducted.TYPE).stream()
                .map(row -> (StockDeducted) toDomainEvent(row))
                .toList();
    }

    /** 5-event-sourcing.md §3.4: deterministic id from (orderId, productId, eventType). */
    public static String deterministicEventId(String orderId, String productId, String eventType) {
        String key = orderId + "|" + productId + "|" + eventType;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String eventIdFor(StockItemEvent event) {
        return switch (event) {
            case StockDeducted d -> deterministicEventId(d.orderId(), d.productId(), StockDeducted.TYPE);
            case StockReleased r -> deterministicEventId(r.orderId(), r.productId(), StockReleased.TYPE);
            case StockAdjusted a -> UUID.randomUUID().toString();
        };
    }

    private static String orderIdOf(StockItemEvent event) {
        return switch (event) {
            case StockDeducted d -> d.orderId();
            case StockReleased r -> r.orderId();
            case StockAdjusted a -> null;
        };
    }

    private StockItemEvent toDomainEvent(StockEventEntity row) {
        Class<? extends StockItemEvent> type = EVENT_TYPES.get(row.getEventType());
        if (type == null) {
            throw new IllegalStateException("Unknown stock event type in store: " + row.getEventType());
        }
        try {
            return objectMapper.readValue(row.getPayload(), type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Corrupt payload for stock event " + row.getEventId(), ex);
        }
    }

    private String toJson(StockItemEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise stock event " + event, ex);
        }
    }
}
