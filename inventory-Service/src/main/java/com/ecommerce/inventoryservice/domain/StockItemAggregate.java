package com.ecommerce.inventoryservice.domain;

import com.ecommerce.inventoryservice.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Write-side of {@code StockItem} (Aggregate Root) — the only event-sourced aggregate in the
 * system. Rehydrated by replaying its event stream (specs/iter2/5-event-sourcing.md §3.4);
 * {@code quantityAvailable} is never persisted directly on the write side.
 *
 * <p>Invariant 3 (specs/iter2/3-domain-models-inventory-service-ddd.md §2.3.1): there is no
 * explicit "initialize" event — a stream comes into existence with its first {@code StockAdjusted}
 * event, starting from quantityAvailable = 0. A stream with zero events is "untracked":
 * {@link #exists()} returns false, and {@code deduct}/{@code release} against it are rejected
 * (invariant 2 — a deduction cannot be validated without a starting quantity).
 *
 * <p>Its {@code id} is a {@link StockItemId} taking the same value as the tracked
 * {@link ProductId} (one product, one stream), so the two are interchangeable for stream lookup.
 */
public final class StockItemAggregate {

    private final StockItemId id;
    private final ProductId productId;
    private Quantity quantityAvailable;
    private long version;

    private StockItemAggregate(ProductId productId) {
        this.productId = productId;
        this.id = productId.asStockItemId();
        this.quantityAvailable = new Quantity(0);
        this.version = 0;
    }

    /** Rehydrates the aggregate by replaying its stream in order. Empty list = untracked product. */
    public static StockItemAggregate rehydrate(ProductId productId, List<StockEvent> orderedEvents) {
        StockItemAggregate aggregate = new StockItemAggregate(productId);
        for (StockEvent event : orderedEvents) {
            aggregate.apply(event);
        }
        return aggregate;
    }

    private void apply(StockEvent event) {
        switch (event.getEventType()) {
            case "StockAdjusted" -> quantityAvailable = quantityAvailable.plus(valueOrZero(event.getDelta()));
            case "StockDeducted" -> quantityAvailable = quantityAvailable.minus(valueOrZero(event.getQuantity()));
            case "StockReleased" -> quantityAvailable = quantityAvailable.plus(valueOrZero(event.getQuantity()));
            default -> throw new IllegalStateException("Unknown stock event type: " + event.getEventType());
        }
        version = event.getAggregateVersion();
    }

    /** Whether this productId has at least one event on its stream (i.e. is "tracked"). */
    public boolean exists() {
        return version > 0;
    }

    public StockItemId getId() {
        return id;
    }

    public ProductId getProductId() {
        return productId;
    }

    public Quantity getQuantityAvailable() {
        return quantityAvailable;
    }

    public long getVersion() {
        return version;
    }

    /** I3 — manual restock/correction. Starts the stream from 0 if previously untracked. */
    public StockEvent adjust(int delta, String reason, String adjustedBy) {
        if (quantityAvailable.value() + delta < 0) {
            throw stockInvariantViolation();
        }
        StockEvent event = new StockEvent(UUID.randomUUID().toString(), productId, "StockAdjusted",
                version + 1, Instant.now(), delta, null, null, reason, adjustedBy,
                payloadJson("delta", delta, "reason", reason, "adjustedBy", adjustedBy));
        apply(event);
        return event;
    }

    /** I2 — deduct on order creation. Rejected if untracked or would go negative. */
    public StockEvent deduct(Quantity quantity, String orderId) {
        if (!exists() || quantityAvailable.value() - quantity.value() < 0) {
            throw stockInvariantViolation();
        }
        StockEvent event = new StockEvent(UUID.randomUUID().toString(), productId, "StockDeducted",
                version + 1, Instant.now(), null, quantity.value(), orderId, null, null,
                payloadJson("quantity", quantity.value(), "orderId", orderId));
        apply(event);
        return event;
    }

    /** I5 — release on order cancellation / payment failure. */
    public StockEvent release(Quantity quantity, String orderId) {
        StockEvent event = new StockEvent(UUID.randomUUID().toString(), productId, "StockReleased",
                version + 1, Instant.now(), null, quantity.value(), orderId, null, null,
                payloadJson("quantity", quantity.value(), "orderId", orderId));
        apply(event);
        return event;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static ApiException stockInvariantViolation() {
        return new ApiException(HttpStatus.CONFLICT, "StockInvariantViolation",
                "Insufficient stock for one or more items");
    }

    private static String payloadJson(Object... keyValuePairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(keyValuePairs[i]).append("\":");
            Object value = keyValuePairs[i + 1];
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append('"').append(value.toString().replace("\"", "\\\"")).append('"');
            }
        }
        return sb.append('}').toString();
    }
}
