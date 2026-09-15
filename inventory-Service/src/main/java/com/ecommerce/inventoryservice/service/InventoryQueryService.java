package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.InventoryReadModel;
import com.ecommerce.inventoryservice.dto.LowStockAlertResponse;
import com.ecommerce.inventoryservice.dto.StockResponse;
import com.ecommerce.inventoryservice.exception.ApiException;
import com.ecommerce.inventoryservice.repository.InventoryReadModelRepository;
import com.ecommerce.inventoryservice.repository.LowStockAlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Query Application Service for Inventory Service — specs/iter2/6-cqrs-command-query.md §3.
 * Covers I1 (check stock) and I4 (list low-stock alerts). Both read from CRUD/projection stores,
 * never from the event stream directly (see 5-event-sourcing.md §3.5).
 */
@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryReadModelRepository readModelRepository;
    private final LowStockAlertRepository lowStockAlertRepository;

    public InventoryQueryService(InventoryReadModelRepository readModelRepository,
                                  LowStockAlertRepository lowStockAlertRepository) {
        this.readModelRepository = readModelRepository;
        this.lowStockAlertRepository = lowStockAlertRepository;
    }

    /** I1 — an untracked productId (no stream yet) is a 404, not a zero-quantity 200. */
    public StockResponse getStock(String productId) {
        InventoryReadModel model = readModelRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound",
                        "No stock record for this product"));
        return new StockResponse(model.getProductId(), model.getQuantityAvailable(),
                model.getQuantityAvailable() > 0, model.getVersion());
    }

    /** I4 — active (unresolved) alerts only. */
    public List<LowStockAlertResponse> listLowStockAlerts() {
        return lowStockAlertRepository.findByResolvedAtIsNull().stream()
                .map(a -> new LowStockAlertResponse(a.getId(), a.getStockItemId(), a.getTriggeredAt(), a.getResolvedAt()))
                .toList();
    }
}
