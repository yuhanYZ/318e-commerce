package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.dto.LowStockAlertResponse;
import com.ecommerce.inventoryservice.dto.StockResponse;
import com.ecommerce.inventoryservice.exception.ApiException;
import com.ecommerce.inventoryservice.repository.InventoryReadModel;
import com.ecommerce.inventoryservice.repository.InventoryReadModelRepository;
import com.ecommerce.inventoryservice.repository.LowStockAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Query application service - 6-cqrs-command-query.md §3.2. Covers I1 and I4.
 *
 * <p>Reads projections and CRUD stores only. It never loads the event stream and never calls an
 * aggregate behaviour, per the CQRS constraint that queries must not mutate.
 */
@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryReadModelRepository readModelRepository;
    private final LowStockAlertRepository alertRepository;

    public InventoryQueryService(InventoryReadModelRepository readModelRepository,
                                 LowStockAlertRepository alertRepository) {
        this.readModelRepository = readModelRepository;
        this.alertRepository = alertRepository;
    }

    /** I1 - CheckStockAvailabilityQuery. An untracked product is a 404, not a zero-quantity 200. */
    public StockResponse getStock(String productId) {
        InventoryReadModel model = readModelRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("No stock record for this product"));
        return new StockResponse(model.getProductId(), model.getQuantityAvailable(),
                model.getQuantityAvailable() > 0, model.getVersion());
    }

    /** I4 - ListLowStockAlertsQuery. Active, unresolved alerts only, oldest first. */
    public List<LowStockAlertResponse> listActiveAlerts() {
        return alertRepository.findByResolvedAtIsNullOrderByTriggeredAtAsc().stream()
                .map(alert -> new LowStockAlertResponse(alert.getId(), alert.getStockItemId(),
                        alert.getTriggeredAt(), alert.getResolvedAt()))
                .toList();
    }
}
