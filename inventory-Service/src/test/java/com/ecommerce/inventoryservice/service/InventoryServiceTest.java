package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.LowStockAlert;
import com.ecommerce.inventoryservice.domain.StockItem;
import com.ecommerce.inventoryservice.dto.request.StockAdjustmentRequest;
import com.ecommerce.inventoryservice.event.OrderItemPayload;
import com.ecommerce.inventoryservice.event.StockReservationFailedEvent;
import com.ecommerce.inventoryservice.event.StockUpdatedEvent;
import com.ecommerce.inventoryservice.messaging.InventoryEventPublisher;
import com.ecommerce.inventoryservice.repository.LowStockAlertRepository;
import com.ecommerce.inventoryservice.repository.StockAdjustmentLogRepository;
import com.ecommerce.inventoryservice.repository.StockItemRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests (no Spring context, no Kafka broker) for the I2/I5 Kafka
 * consumer logic that OrderEventListener delegates to InventoryService, and
 * for the low-stock alert evaluation shared by every stock-changing path.
 * These have no REST endpoint, so they aren't covered by
 * InventoryControllerIntegrationTest.
 */
class InventoryServiceTest {

    private final StockItemRepository stockItemRepository = org.mockito.Mockito.mock(StockItemRepository.class);
    private final StockAdjustmentLogRepository stockAdjustmentLogRepository = org.mockito.Mockito.mock(StockAdjustmentLogRepository.class);
    private final LowStockAlertRepository lowStockAlertRepository = org.mockito.Mockito.mock(LowStockAlertRepository.class);
    private final InventoryEventPublisher inventoryEventPublisher = org.mockito.Mockito.mock(InventoryEventPublisher.class);

    private final InventoryService inventoryService = new InventoryService(
            stockItemRepository, stockAdjustmentLogRepository, lowStockAlertRepository, inventoryEventPublisher, 10);

    @Test
    void deductForOrder_sufficientStock_deductsAndPublishesStockUpdated() {
        StockItem stockItem = StockItem.builder().id("STK-1").productId("PRD-1").quantityAvailable(20).lowStockThreshold(10).build();
        when(stockItemRepository.findByProductId("PRD-1")).thenReturn(Optional.of(stockItem));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lowStockAlertRepository.findByStockItemIdAndResolvedAtIsNull("STK-1")).thenReturn(Optional.empty());

        inventoryService.deductForOrder("ORD-1", List.of(new OrderItemPayload("PRD-1", 5)));

        assertThat(stockItem.getQuantityAvailable()).isEqualTo(15);
        verify(inventoryEventPublisher).publishStockUpdated(new StockUpdatedEvent("PRD-1", 15));
        verify(inventoryEventPublisher, never()).publishStockReservationFailed(any());
    }

    @Test
    void deductForOrder_insufficientStock_deductsNothingAndPublishesFailure() {
        StockItem stockItem = StockItem.builder().id("STK-1").productId("PRD-1").quantityAvailable(2).lowStockThreshold(10).build();
        when(stockItemRepository.findByProductId("PRD-1")).thenReturn(Optional.of(stockItem));

        inventoryService.deductForOrder("ORD-1", List.of(new OrderItemPayload("PRD-1", 5)));

        assertThat(stockItem.getQuantityAvailable()).isEqualTo(2);
        verify(stockItemRepository, never()).save(any());
        verify(inventoryEventPublisher).publishStockReservationFailed(
                new StockReservationFailedEvent("ORD-1", List.of("PRD-1")));
    }

    @Test
    void deductForOrder_unknownProduct_isTreatedAsInsufficientStock() {
        when(stockItemRepository.findByProductId("PRD-X")).thenReturn(Optional.empty());

        inventoryService.deductForOrder("ORD-1", List.of(new OrderItemPayload("PRD-X", 1)));

        verify(inventoryEventPublisher).publishStockReservationFailed(
                new StockReservationFailedEvent("ORD-1", List.of("PRD-X")));
    }

    @Test
    void deductForOrder_oneOfTwoItemsInsufficient_deductsNeitherItem() {
        StockItem sufficient = StockItem.builder().id("STK-1").productId("PRD-1").quantityAvailable(20).lowStockThreshold(10).build();
        StockItem insufficient = StockItem.builder().id("STK-2").productId("PRD-2").quantityAvailable(1).lowStockThreshold(10).build();
        when(stockItemRepository.findByProductId("PRD-1")).thenReturn(Optional.of(sufficient));
        when(stockItemRepository.findByProductId("PRD-2")).thenReturn(Optional.of(insufficient));

        inventoryService.deductForOrder("ORD-1",
                List.of(new OrderItemPayload("PRD-1", 5), new OrderItemPayload("PRD-2", 5)));

        assertThat(sufficient.getQuantityAvailable()).isEqualTo(20);
        verify(stockItemRepository, never()).save(any());
        verify(inventoryEventPublisher).publishStockReservationFailed(
                new StockReservationFailedEvent("ORD-1", List.of("PRD-2")));
    }

    @Test
    void releaseForOrder_existingProduct_restoresQuantity() {
        StockItem stockItem = StockItem.builder().id("STK-1").productId("PRD-1").quantityAvailable(3).lowStockThreshold(10).build();
        when(stockItemRepository.findByProductId("PRD-1")).thenReturn(Optional.of(stockItem));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lowStockAlertRepository.findByStockItemIdAndResolvedAtIsNull("STK-1")).thenReturn(Optional.empty());

        inventoryService.releaseForOrder("ORD-1", List.of(new OrderItemPayload("PRD-1", 5)));

        assertThat(stockItem.getQuantityAvailable()).isEqualTo(8);
        verify(stockItemRepository).save(stockItem);
    }

    @Test
    void releaseForOrder_unknownProduct_isIgnored() {
        when(stockItemRepository.findByProductId("PRD-X")).thenReturn(Optional.empty());

        inventoryService.releaseForOrder("ORD-1", List.of(new OrderItemPayload("PRD-X", 5)));

        verify(stockItemRepository, never()).save(any());
    }

    @Test
    void adjustStock_droppingBelowThreshold_opensNewAlert() {
        StockItem stockItem = StockItem.builder().id("STK-1").productId("PRD-1").quantityAvailable(15).lowStockThreshold(10).build();
        when(stockItemRepository.findByProductId("PRD-1")).thenReturn(Optional.of(stockItem));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lowStockAlertRepository.findByStockItemIdAndResolvedAtIsNull("STK-1")).thenReturn(Optional.empty());

        StockAdjustmentRequest request = new StockAdjustmentRequest(-8, "Bulk sale");
        inventoryService.adjustStock("PRD-1", request, "ADMIN-1");

        verify(lowStockAlertRepository).save(argThat(alert ->
                alert.getStockItemId().equals("STK-1") && alert.getResolvedAt() == null));
    }

    @Test
    void adjustStock_recoveringAboveThreshold_resolvesActiveAlert() {
        StockItem stockItem = StockItem.builder().id("STK-1").productId("PRD-1").quantityAvailable(2).lowStockThreshold(10).build();
        LowStockAlert activeAlert = LowStockAlert.builder().id("ALERT-1").stockItemId("STK-1").build();
        when(stockItemRepository.findByProductId("PRD-1")).thenReturn(Optional.of(stockItem));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lowStockAlertRepository.findByStockItemIdAndResolvedAtIsNull("STK-1")).thenReturn(Optional.of(activeAlert));

        StockAdjustmentRequest request = new StockAdjustmentRequest(50, "Restock");
        inventoryService.adjustStock("PRD-1", request, "ADMIN-1");

        assertThat(activeAlert.getResolvedAt()).isNotNull();
        verify(lowStockAlertRepository).save(activeAlert);
    }
}
