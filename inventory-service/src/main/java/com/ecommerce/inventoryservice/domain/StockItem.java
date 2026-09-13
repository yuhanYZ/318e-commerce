package com.ecommerce.inventoryservice.domain;

import com.ecommerce.inventoryservice.domain.event.StockAdjusted;
import com.ecommerce.inventoryservice.domain.event.StockDeducted;
import com.ecommerce.inventoryservice.domain.event.StockItemEvent;
import com.ecommerce.inventoryservice.domain.event.StockReleased;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code StockItemAggregate} (root: StockItem) - domain-models §2.3.1, 5-event-sourcing.md.
 *
 * <p>The only event-sourced aggregate in the system. It is never persisted as a row: its state is
 * rebuilt by replaying its stream with {@link #rehydrate}, and every change is recorded as a new
 * {@link StockItemEvent}. Plain Java with no Spring or JPA dependency, so the rules below can be
 * reasoned about, and tested, in isolation.
 *
 * <p>Invariants:
 * <ol>
 *   <li>{@code quantityAvailable >= 0} at all times.</li>
 *   <li>A deduction cannot take {@code quantityAvailable} below zero.</li>
 *   <li>There is no initialise event. A stream begins with its first {@link StockAdjusted}, from an
 *       implicit quantity of 0. Deducting or releasing on a product with no stream is rejected.</li>
 * </ol>
 *
 * <p>Each behaviour validates first and only then applies its event, so a rejected command leaves
 * the aggregate, and the event store, untouched.
 */
public final class StockItem {

    private final String productId;
    private Quantity quantityAvailable = Quantity.ZERO;
    private long version;
    private final List<StockItemEvent> pendingEvents = new ArrayList<>();

    private StockItem(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        this.productId = productId;
    }

    /** Rebuilds the aggregate by applying its events in version order. An empty history means untracked. */
    public static StockItem rehydrate(String productId, List<StockItemEvent> history) {
        StockItem item = new StockItem(productId);
        history.forEach(item::apply);
        return item;
    }

    /**
     * Behaviour {@code adjust(delta, reason, adjustedBy)} - I3. Positive restocks, negative removes.
     * The first adjustment on an untracked product starts its stream.
     *
     * @throws IllegalArgumentException if delta is zero, which changes nothing
     * @throws StockInvariantViolationException if the result would be negative
     */
    public StockAdjusted adjust(int delta, String reason, String adjustedBy) {
        if (delta == 0) {
            throw new IllegalArgumentException("delta must not be zero");
        }
        if (adjustedBy == null || adjustedBy.isBlank()) {
            throw new IllegalArgumentException("adjustedBy is required");
        }
        if ((long) quantityAvailable.value() + delta < 0) {
            throw new StockInvariantViolationException(
                    "Adjustment of " + delta + " would take stock for " + productId + " below zero");
        }
        StockAdjusted event = new StockAdjusted(productId, delta, reason, adjustedBy, Instant.now());
        record(event);
        return event;
    }

    /**
     * Behaviour {@code deduct(quantity, orderId)} - I2.
     *
     * @throws StockInvariantViolationException if the product is untracked or stock is insufficient
     */
    public StockDeducted deduct(int quantity, String orderId) {
        requirePositive(quantity);
        requireOrderId(orderId);
        if (!isTracked()) {
            throw new StockInvariantViolationException("No stock record for " + productId);
        }
        if (quantityAvailable.value() < quantity) {
            throw new StockInvariantViolationException("Insufficient stock for " + productId
                    + ": requested " + quantity + ", available " + quantityAvailable.value());
        }
        StockDeducted event = new StockDeducted(productId, quantity, orderId, Instant.now());
        record(event);
        return event;
    }

    /**
     * Behaviour {@code release(quantity, orderId)} - I5. Callers pass the quantity that was actually
     * deducted for the order, never a quantity taken from an incoming message.
     *
     * @throws StockInvariantViolationException if the product is untracked
     */
    public StockReleased release(int quantity, String orderId) {
        requirePositive(quantity);
        requireOrderId(orderId);
        if (!isTracked()) {
            throw new StockInvariantViolationException("No stock record for " + productId);
        }
        StockReleased event = new StockReleased(productId, quantity, orderId, Instant.now());
        record(event);
        return event;
    }

    /** Returns events raised since rehydration and clears them, so each is appended exactly once. */
    public List<StockItemEvent> pullPendingEvents() {
        List<StockItemEvent> pending = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return pending;
    }

    /** Stream version of the first pending event, for assigning {@code aggregate_version} on append. */
    public long firstPendingVersion() {
        return version - pendingEvents.size() + 1;
    }

    /** A product is tracked once its stream has at least one event. */
    public boolean isTracked() {
        return version > 0;
    }

    public String getProductId() {
        return productId;
    }

    public Quantity getQuantityAvailable() {
        return quantityAvailable;
    }

    public long getVersion() {
        return version;
    }

    private void record(StockItemEvent event) {
        apply(event);
        pendingEvents.add(event);
    }

    /** State transition for one event. Shared by replay and by new events, so both stay identical. */
    private void apply(StockItemEvent event) {
        int next = switch (event) {
            case StockAdjusted adjusted -> quantityAvailable.value() + adjusted.delta();
            case StockDeducted deducted -> quantityAvailable.value() - deducted.quantity();
            case StockReleased released -> quantityAvailable.value() + released.quantity();
        };
        quantityAvailable = new Quantity(next);
        version++;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
    }

    private static void requireOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
    }
}
