package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.StockAdjustmentLog;
import com.ecommerce.inventoryservice.domain.StockInvariantViolationException;
import com.ecommerce.inventoryservice.domain.StockItem;
import com.ecommerce.inventoryservice.domain.event.StockAdjusted;
import com.ecommerce.inventoryservice.domain.event.StockDeducted;
import com.ecommerce.inventoryservice.domain.event.StockItemEvent;
import com.ecommerce.inventoryservice.domain.event.StockReleased;
import com.ecommerce.inventoryservice.dto.StockResponse;
import com.ecommerce.inventoryservice.messaging.StockLevelChanged;
import com.ecommerce.inventoryservice.messaging.StockLevelChangedPublisher;
import com.ecommerce.inventoryservice.repository.InventoryReadModel;
import com.ecommerce.inventoryservice.repository.InventoryReadModelRepository;
import com.ecommerce.inventoryservice.repository.StockAdjustmentLogRepository;
import com.ecommerce.inventoryservice.repository.StockEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Command application service - 6-cqrs-command-query.md §3.1. Covers I3 over REST, and I2 and I5 for
 * the Kafka consumers.
 *
 * <p>Every command follows the same event-sourced cycle: load the stream, run the aggregate behaviour,
 * append the new event, update the read model, and hand the new level to the low-stock job. All of it
 * commits in one transaction.
 *
 * <p>Concurrency (5-event-sourcing.md §3.4): a concurrent append with a stale version breaks the
 * unique (aggregate_id, aggregate_version) constraint. The whole transaction is then retried from a
 * fresh load, up to {@value #MAX_ATTEMPTS} attempts. Transactions are managed per attempt with a
 * TransactionTemplate, because a failed flush marks a transaction rollback-only and it cannot be reused.
 */
@Service
public class InventoryCommandService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandService.class);

    static final int MAX_ATTEMPTS = 3;

    private final StockEventStore eventStore;
    private final InventoryReadModelRepository readModelRepository;
    private final StockAdjustmentLogRepository adjustmentLogRepository;
    private final StockLevelChangedPublisher stockLevelChangedPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public InventoryCommandService(StockEventStore eventStore,
                                   InventoryReadModelRepository readModelRepository,
                                   StockAdjustmentLogRepository adjustmentLogRepository,
                                   StockLevelChangedPublisher stockLevelChangedPublisher,
                                   ApplicationEventPublisher eventPublisher,
                                   PlatformTransactionManager transactionManager) {
        this.eventStore = eventStore;
        this.readModelRepository = readModelRepository;
        this.adjustmentLogRepository = adjustmentLogRepository;
        this.stockLevelChangedPublisher = stockLevelChangedPublisher;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * I3 - AdjustStockCommand. The first adjustment for a product starts its stream.
     *
     * @throws StockInvariantViolationException if the adjustment would take stock below zero
     * @throws IllegalArgumentException if delta is zero
     */
    public StockResponse adjustStock(String productId, int delta, String reason, String adjustedBy) {
        return inTransactionWithRetry(() -> {
            StockItem item = eventStore.load(productId);
            item.adjust(delta, reason, adjustedBy);

            List<StockItemEvent> appended = commit(item);
            appended.stream()
                    .filter(StockAdjusted.class::isInstance)
                    .map(StockAdjusted.class::cast)
                    .forEach(event -> adjustmentLogRepository.save(StockAdjustmentLog.from(event)));

            return new StockResponse(item.getProductId(), item.getQuantityAvailable().value(),
                    !item.getQuantityAvailable().isZero(), item.getVersion());
        });
    }

    /**
     * I2 - DeductStockOnOrderCreatedCommand, for one order line.
     *
     * <p>Idempotent: a redelivered message finds its deterministic event id already stored and does
     * nothing. A rejection (untracked product, insufficient stock) is logged and swallowed, as the
     * spec requires; throwing from a Kafka consumer would only cause the same message to be redelivered.
     */
    public void deductStock(String orderId, String productId, int quantity) {
        try {
            inTransactionWithRetry(() -> {
                if (eventStore.exists(StockEventStore.deterministicEventId(orderId, productId, StockDeducted.TYPE))) {
                    log.debug("I2: deduction for order {} product {} already recorded, ignoring redelivery",
                            orderId, productId);
                    return null;
                }
                StockItem item = eventStore.load(productId);
                item.deduct(quantity, orderId);
                commit(item);
                return null;
            });
        } catch (StockInvariantViolationException | IllegalArgumentException ex) {
            log.warn("I2: rejected deduction for order {} product {} quantity {}: {}",
                    orderId, productId, quantity, ex.getMessage());
        }
    }

    /**
     * I5 - ReleaseStockOnOrderCancelledCommand, for one order line.
     *
     * <p>Releases exactly the quantity recorded when stock was deducted for this order and product.
     * If nothing was ever deducted, for example because the deduction was rejected, nothing is
     * released, so a cancellation can never add stock that was never taken. Idempotent through the
     * release's deterministic event id, which also means a cancellation and a failed payment for the
     * same order release only once between them.
     */
    public void releaseStock(String orderId, String productId) {
        try {
            inTransactionWithRetry(() -> {
                Optional<StockDeducted> deduction = eventStore.findDeduction(orderId, productId);
                if (deduction.isEmpty()) {
                    log.info("I5: no deduction recorded for order {} product {}, nothing to release",
                            orderId, productId);
                    return null;
                }
                if (eventStore.exists(StockEventStore.deterministicEventId(orderId, productId, StockReleased.TYPE))) {
                    log.debug("I5: release for order {} product {} already recorded, ignoring", orderId, productId);
                    return null;
                }
                StockItem item = eventStore.load(productId);
                item.release(deduction.get().quantity(), orderId);
                commit(item);
                return null;
            });
        } catch (StockInvariantViolationException | IllegalArgumentException ex) {
            log.warn("I5: rejected release for order {} product {}: {}", orderId, productId, ex.getMessage());
        }
    }

    /**
     * I5 for a PaymentFailedEvent, which carries only the order id. The order's lines are recovered
     * from its StockDeducted history, as domain-models §2.4.2 specifies, then released one by one.
     */
    public void releaseAllStockForOrder(String orderId) {
        eventStore.findDeductionsForOrder(orderId).stream()
                .map(StockDeducted::productId)
                .distinct()
                .forEach(productId -> releaseStock(orderId, productId));
    }

    /** Appends pending events, projects the read model, and notifies the low-stock job. */
    private List<StockItemEvent> commit(StockItem item) {
        List<StockItemEvent> appended = eventStore.append(item);

        Instant now = Instant.now();
        InventoryReadModel readModel = readModelRepository.findById(item.getProductId())
                .orElseGet(() -> new InventoryReadModel(item.getProductId()));
        readModel.project(item.getQuantityAvailable().value(), item.getVersion(), now);
        readModelRepository.save(readModel);

        appended.forEach(eventPublisher::publishEvent);
        stockLevelChangedPublisher.publish(new StockLevelChanged(
                item.getProductId(), item.getQuantityAvailable().value(), item.getVersion(), now));
        return appended;
    }

    private <T> T inTransactionWithRetry(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                log.info("Concurrent stock update detected, retrying (attempt {} of {})", attempt + 1, MAX_ATTEMPTS);
            }
        }
    }
}
