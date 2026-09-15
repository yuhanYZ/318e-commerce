package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.InventoryReadModel;
import com.ecommerce.inventoryservice.domain.LowStockAlert;
import com.ecommerce.inventoryservice.domain.StockAdjustmentLog;
import com.ecommerce.inventoryservice.domain.StockEvent;
import com.ecommerce.inventoryservice.domain.StockItemAggregate;
import com.ecommerce.inventoryservice.dto.StockAdjustmentRequest;
import com.ecommerce.inventoryservice.dto.StockResponse;
import com.ecommerce.inventoryservice.exception.ApiException;
import com.ecommerce.inventoryservice.repository.InventoryReadModelRepository;
import com.ecommerce.inventoryservice.repository.LowStockAlertRepository;
import com.ecommerce.inventoryservice.repository.StockAdjustmentLogRepository;
import com.ecommerce.inventoryservice.repository.StockEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Command Application Service for Inventory Service — specs/iter2/6-cqrs-command-query.md §3.
 * Covers I3 (adjust) and I2/I5 ({@link #deductStockOnOrderCreated}/{@link #releaseStockOnOrderCancelled}/
 * {@link #releaseStockForFailedPayment} — invoked by the {@code Consumer<T>} beans in
 * {@code config.KafkaConsumerConfig}, active only under the "kafka" Spring profile so `mvn test`
 * never binds against a real broker).
 */
@Service
@Transactional
public class InventoryCommandService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandService.class);

    private final StockEventRepository stockEventRepository;
    private final InventoryReadModelRepository readModelRepository;
    private final StockAdjustmentLogRepository adjustmentLogRepository;
    private final LowStockAlertRepository lowStockAlertRepository;
    private final InventoryPolicyService policyService;

    public InventoryCommandService(StockEventRepository stockEventRepository,
                                    InventoryReadModelRepository readModelRepository,
                                    StockAdjustmentLogRepository adjustmentLogRepository,
                                    LowStockAlertRepository lowStockAlertRepository,
                                    InventoryPolicyService policyService) {
        this.stockEventRepository = stockEventRepository;
        this.readModelRepository = readModelRepository;
        this.adjustmentLogRepository = adjustmentLogRepository;
        this.lowStockAlertRepository = lowStockAlertRepository;
        this.policyService = policyService;
    }

    /**
     * I3 — Adjust Stock (Restock). Validation (would-go-negative -> 409) happens inside {@link
     * StockItemAggregate#adjust} BEFORE any repository write in this method, so there is no
     * save-then-throw sequence here and therefore no need for a noRollbackFor override (see the
     * Bug #3 lesson from user-service's login lockout).
     */
    public StockResponse adjustStock(String productId, StockAdjustmentRequest request, String adjustedBy) {
        List<StockEvent> events = stockEventRepository.findByAggregateIdOrderByAggregateVersionAsc(productId);
        StockItemAggregate aggregate = StockItemAggregate.rehydrate(productId, events);

        StockEvent newEvent = aggregate.adjust(request.delta(), request.reason(), adjustedBy);
        stockEventRepository.save(newEvent);

        upsertReadModel(aggregate);

        adjustmentLogRepository.save(new StockAdjustmentLog(UUID.randomUUID().toString(), productId, adjustedBy,
                request.delta(), request.reason(), Instant.now()));

        evaluateLowStockAlert(productId, aggregate.getQuantityAvailable());

        return toResponse(aggregate);
    }

    /**
     * I2 — deduct stock on OrderCreatedEvent (one call per order line item, made by the
     * {@code deductStockOnOrderCreated} Consumer bean). Idempotent per (productId, orderId) via
     * the event store: a redelivered message is a silent no-op. Per the I2 command's spec row in
     * 6-cqrs-command-query.md §6 ("rejected and logged if the productId has no stream or stock is
     * insufficient"), a business-rule rejection is caught and logged here rather than thrown — a
     * Kafka consumer must not throw on an ordinary rejection, or it risks endless redelivery.
     */
    public void deductStockOnOrderCreated(String productId, int quantity, String orderId) {
        if (stockEventRepository.existsByAggregateIdAndOrderIdAndEventType(productId, orderId, "StockDeducted")) {
            return;
        }
        List<StockEvent> events = stockEventRepository.findByAggregateIdOrderByAggregateVersionAsc(productId);
        StockItemAggregate aggregate = StockItemAggregate.rehydrate(productId, events);
        try {
            StockEvent newEvent = aggregate.deduct(quantity, orderId);
            stockEventRepository.save(newEvent);
            upsertReadModel(aggregate);
            evaluateLowStockAlert(productId, aggregate.getQuantityAvailable());
        } catch (ApiException ex) {
            log.warn("I2: rejected stock deduction for productId={}, orderId={}, quantity={}: {}",
                    productId, orderId, quantity, ex.getMessage());
        }
    }

    /**
     * I5 — release stock on OrderCancelledEvent (one call per order line item, made by the
     * {@code releaseStockOnOrderCancelled} Consumer bean) or via {@link #releaseStockForFailedPayment}.
     * Idempotent per (productId, orderId): a redelivered release is a silent no-op, and a release
     * with no matching prior deduction on record is logged and skipped (nothing to release).
     */
    public void releaseStockOnOrderCancelled(String productId, int quantity, String orderId) {
        if (stockEventRepository.existsByAggregateIdAndOrderIdAndEventType(productId, orderId, "StockReleased")) {
            return;
        }
        if (!stockEventRepository.existsByAggregateIdAndOrderIdAndEventType(productId, orderId, "StockDeducted")) {
            log.warn("I5: skipping release for productId={}, orderId={} — no prior StockDeducted event on record",
                    productId, orderId);
            return;
        }
        List<StockEvent> events = stockEventRepository.findByAggregateIdOrderByAggregateVersionAsc(productId);
        StockItemAggregate aggregate = StockItemAggregate.rehydrate(productId, events);
        StockEvent newEvent = aggregate.release(quantity, orderId);
        stockEventRepository.save(newEvent);
        upsertReadModel(aggregate);
        evaluateLowStockAlert(productId, aggregate.getQuantityAvailable());
    }

    /**
     * I5 — release triggered by PaymentFailedEvent, whose payload carries only an orderId (no
     * item list — see integration.PaymentFailedEvent's Javadoc). Reconstructs the order's
     * previously-deducted lines from the event store and releases each through the same
     * idempotent {@link #releaseStockOnOrderCancelled} path.
     */
    public void releaseStockForFailedPayment(String orderId) {
        List<StockEvent> deductEvents = stockEventRepository.findByOrderIdAndEventType(orderId, "StockDeducted");
        for (StockEvent deductEvent : deductEvents) {
            releaseStockOnOrderCancelled(deductEvent.getAggregateId(), deductEvent.getQuantity(), orderId);
        }
    }

    private void upsertReadModel(StockItemAggregate aggregate) {
        InventoryReadModel model = readModelRepository.findById(aggregate.getProductId())
                .orElseGet(() -> new InventoryReadModel(aggregate.getProductId(), 0, 0, Instant.now()));
        model.setQuantityAvailable(aggregate.getQuantityAvailable());
        model.setVersion(aggregate.getVersion());
        model.setUpdatedAt(Instant.now());
        readModelRepository.save(model);
    }

    /** I4 — trigger a new alert when crossing below the threshold; resolve it when clearing back above. */
    private void evaluateLowStockAlert(String productId, int quantityAvailable) {
        boolean below = policyService.isBelowThreshold(quantityAvailable);
        Optional<LowStockAlert> openAlert = lowStockAlertRepository.findByStockItemIdAndResolvedAtIsNull(productId);
        if (below && openAlert.isEmpty()) {
            lowStockAlertRepository.save(new LowStockAlert(UUID.randomUUID().toString(), productId, Instant.now()));
        } else if (!below && openAlert.isPresent()) {
            LowStockAlert alert = openAlert.get();
            alert.resolve(Instant.now());
            lowStockAlertRepository.save(alert);
        }
    }

    private static StockResponse toResponse(StockItemAggregate aggregate) {
        return new StockResponse(aggregate.getProductId(), aggregate.getQuantityAvailable(),
                aggregate.getQuantityAvailable() > 0, aggregate.getVersion());
    }
}
