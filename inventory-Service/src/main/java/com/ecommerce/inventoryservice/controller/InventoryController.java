package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.LowStockAlertResponse;
import com.ecommerce.inventoryservice.dto.StockAdjustmentRequest;
import com.ecommerce.inventoryservice.dto.StockResponse;
import com.ecommerce.inventoryservice.service.InventoryCommandService;
import com.ecommerce.inventoryservice.service.InventoryQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** specs/iter2/openapi-contract-v2.yaml paths /inventory/*. */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryCommandService commandService;
    private final InventoryQueryService queryService;

    public InventoryController(InventoryCommandService commandService, InventoryQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping("/alerts")
    public List<LowStockAlertResponse> listLowStockAlerts() {
        return queryService.listLowStockAlerts();
    }

    @GetMapping("/{productId}")
    public StockResponse getStock(@PathVariable String productId) {
        return queryService.getStock(productId);
    }

    @PostMapping("/{productId}/adjust")
    public StockResponse adjustStock(@PathVariable String productId,
                                      @Valid @RequestBody StockAdjustmentRequest request,
                                      Authentication authentication) {
        return commandService.adjustStock(productId, request, authentication.getName());
    }
}
