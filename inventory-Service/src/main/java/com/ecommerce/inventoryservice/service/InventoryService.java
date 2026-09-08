package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.LowStockAlert;
import com.ecommerce.inventoryservice.domain.StockAdjustmentLog;
import com.ecommerce.inventoryservice.domain.StockItem;
import com.ecommerce.inventoryservice.dto.request.StockAdjustmentRequest;
import com.ecommerce.inventoryservice.dto.response.LowStockAlertResponse;
import com.ecommerce.inventoryservice.dto.response.StockResponse;
import com.ecommerce.inventoryservice.event.OrderItemPayload;
import com.ecommerce.inventoryservice.event.StockReservationFailedEvent;
import com.ecommerce.inventoryservice.event.StockUpdatedEvent;
import com.ecommerce.inventoryservice.exception.InvalidStockAdjustmentException;
import com.ecommerce.inventoryservice.exception.StockItemNotFoundException;
import com.ecommerce.inventoryservice.messaging.InventoryEventPublisher;
import com.ecommerce.inventoryservice.repository.LowStockAlertRepository;
import com.ecommerce.inventoryservice.repository.StockAdjustmentLogRepository;
import com.ecommerce.inventoryservice.repository.StockItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business logic for the Inventory Service. Implements I1-I5 from
 * specs/2-user-stories.md, per the Service Layer responsibilities in
 * specs/1-technical-architecture.md.
 * <p>
 * Deliberate deviation from specs/1-technical-architecture.md: User Story I4
 * describes low-stock evaluation as happening "经由 Spring Cloud Stream 流处理
 * 任务" (via a Spring Cloud Stream stream-processing job). This service instead
 * runs {@link #evaluateLowStockAlert} synchronously, in the same transaction
 * as every stock-quantity change (adjustStock / deductForOrder / releaseForOrder).
 * The evaluation only ever reads the one StockItem row that transaction just
 * wrote - there is no cross-record aggregation or windowing that would justify
 * a separate streaming framework - so adding Spring Cloud Stream's BOM and a
 * binder here would add real version-matching risk for logic that is purely
 * local to a single row. Flagged for the user as an explicit interpretation
 * choice, not an oversight.
 */
@Service
@Slf4j
public class InventoryService {

    private final StockItemRepository stockItemRepository;
    private final StockAdjustmentLogRepository stockAdjustmentLogRepository;
    private final LowStockAlertRepository lowStockAlertRepository;
    private final InventoryEventPublisher inventoryEventPublisher;
    private final int defaultLowStockThreshold;

    public InventoryService(
            StockItemRepository stockItemRepository,
            StockAdjustmentLogRepository stockAdjustmentLogRepository,
            LowStockAlertRepository lowStockAlertRepository,
            InventoryEventPublisher inventoryEventPublisher,
            @Value("${app.inventory.default-low-stock-threshold}") int defaultLowStockThreshold) {
        this.stockItemRepository = stockItemRepository;
        this.stockAdjustmentLogRepository = stockAdjustmentLogRepository;
        this.lowStockAlertRepository = lowStockAlertRepository;
        this.inventoryEventPublisher = inventoryEventPublisher;
        this.defaultLowStockThreshold = defaultLowStockThreshold;
    }

    /** I1: look up current stock for a product; 404 if no StockItem record exists yet for it. */
    @Transactional(readOnly = true)
    public StockResponse getStock(String productId) {
        StockItem stockItem = stockItemRepository.findByProductId(productId)
                .orElseThrow(() -> new StockItemNotFoundException("No stock record for productId=" + productId));
        return toResponse(stockItem);
    }

    /**
     * I3: Admin restock/adjustment. Auto-creates a StockItem (starting at 0,
     * with the configured default threshold) the first time a product is
     * adjusted, since nothing else in this service creates one - Product
     * Service owns product creation and has no obligation to also call this
     * service. Rejects any adjustment that would make quantityAvailable
     * negative (User Story I3 step ii).
     */
    @Transactional
    public StockResponse adjustStock(String productId, StockAdjustmentRequest request, String adminUserId) {
        StockItem stockItem = stockItemRepository.findByProductId(productId)
                .orElseGet(() -> StockItem.builder()
                        .productId(productId)
                        .quantityAvailable(0)
                        .lowStockThreshold(defaultLowStockThreshold)
                        .build());

        int newQuantity = stockItem.getQuantityAvailable() + request.getDelta();
        if (newQuantity < 0) {
            throw new InvalidStockAdjustmentException(
                    "Adjustment of " + request.getDelta() + " would make stock negative for productId=" + productId);
        }
        stockItem.setQuantityAvailable(newQuantity);
        stockItem = stockItemRepository.save(stockItem);

        stockAdjustmentLogRepository.save(StockAdjustmentLog.builder()
                .stockItemId(stockItem.getId())
                .adjustedBy(adminUserId)
                .delta(request.getDelta())
                .reason(request.getReason())
                .build());

        evaluateLowStockAlert(stockItem);

        return toResponse(stockItem);
    }

    /** I4: active (unresolved) low-stock alerts. */
    @Transactional(readOnly = true)
    public List<LowStockAlertResponse> listActiveAlerts() {
        return lowStockAlertRepository.findAllByResolvedAtIsNull().stream()
                .map(a -> new LowStockAlertResponse(a.getId(), a.getStockItemId(), a.getTriggeredAt(), a.getResolvedAt()))
                .toList();
    }

    /**
     * I2: consumes OrderCreatedEvent. All-or-nothing per order: if ANY item
     * lacks sufficient stock, nothing is deducted and StockReservationFailedEvent
     * is published instead (User Story I2 step iii); a productId with no
     * existing StockItem record is treated as 0 available. On full success,
     * deducts every item and publishes one StockUpdatedEvent per item.
     */
    @Transactional
    public void deductForOrder(String orderId, List<OrderItemPayload> items) {
        List<String> insufficient = new ArrayList<>();
        Map<String, StockItem> byProductId = new LinkedHashMap<>();

        for (OrderItemPayload item : items) {
            StockItem stockItem = stockItemRepository.findByProductId(item.getProductId()).orElse(null);
            int available = stockItem != null ? stockItem.getQuantityAvailable() : 0;
            if (available < item.getQuantity()) {
                insufficient.add(item.getProductId());
            } else {
                byProductId.put(item.getProductId(), stockItem);
            }
        }

        if (!insufficient.isEmpty()) {
            log.warn("Insufficient stock deducting for orderId={}, productIds={}", orderId, insufficient);
            inventoryEventPublisher.publishStockReservationFailed(
                    new StockReservationFailedEvent(orderId, insufficient));
            return;
        }

        for (OrderItemPayload item : items) {
            StockItem stockItem = byProductId.get(item.getProductId());
            stockItem.setQuantityAvailable(stockItem.getQuantityAvailable() - item.getQuantity());
            stockItem = stockItemRepository.save(stockItem);
            evaluateLowStockAlert(stockItem);
            inventoryEventPublisher.publishStockUpdated(
                    new StockUpdatedEvent(stockItem.getProductId(), stockItem.getQuantityAvailable()));
        }
    }

    /**
     * I5: consumes OrderCancelledEvent / PaymentFailedEvent. Restores
     * previously-deducted quantities. A missing StockItem (shouldn't happen,
     * since deductForOrder requires one to exist in order to have succeeded)
     * is logged and skipped rather than silently creating a new record with an
     * unknown threshold.
     */
    @Transactional
    public void releaseForOrder(String orderId, List<OrderItemPayload> items) {
        for (OrderItemPayload item : items) {
            Optional<StockItem> stockItem = stockItemRepository.findByProductId(item.getProductId());
            if (stockItem.isEmpty()) {
                log.warn("Cannot release stock for unknown productId={} (orderId={})", item.getProductId(), orderId);
                continue;
            }
            StockItem existing = stockItem.get();
            existing.setQuantityAvailable(existing.getQuantityAvailable() + item.getQuantity());
            StockItem saved = stockItemRepository.save(existing);
            evaluateLowStockAlert(saved);
        }
    }

    /**
     * I4: opens a new alert when quantityAvailable drops below the item's
     * threshold and none is already active; resolves the active alert once
     * quantity recovers to/above the threshold. Never opens a second alert
     * while one is already unresolved (step iii).
     */
    private void evaluateLowStockAlert(StockItem stockItem) {
        boolean isLow = stockItem.getQuantityAvailable() < stockItem.getLowStockThreshold();
        Optional<LowStockAlert> existing = lowStockAlertRepository.findByStockItemIdAndResolvedAtIsNull(stockItem.getId());

        if (isLow && existing.isEmpty()) {
            lowStockAlertRepository.save(LowStockAlert.builder().stockItemId(stockItem.getId()).build());
        } else if (!isLow && existing.isPresent()) {
            LowStockAlert alert = existing.get();
            alert.setResolvedAt(LocalDateTime.now());
            lowStockAlertRepository.save(alert);
        }
    }

    private StockResponse toResponse(StockItem stockItem) {
        return new StockResponse(
                stockItem.getProductId(),
                stockItem.getQuantityAvailable(),
                stockItem.getQuantityAvailable() > 0);
    }
}
