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

/**
 * Presentation layer for I1, I3 and I4 - 4-api-endpoints-v2.md §4.
 *
 * <p>I2 and I5 have no endpoint; they are Kafka consumers (see KafkaConsumerConfig). Authorization is
 * enforced by SecurityConfig. The admin's user id for the audit trail comes from the JWT subject,
 * never from the request body.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryCommandService commandService;
    private final InventoryQueryService queryService;

    public InventoryController(InventoryCommandService commandService, InventoryQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** I4 - active low-stock alerts. Admin only. Mapped as a literal path, so it wins over {productId}. */
    @GetMapping("/alerts")
    public List<LowStockAlertResponse> listLowStockAlerts() {
        return queryService.listActiveAlerts();
    }

    /** I1 - stock availability from the read model. Public. */
    @GetMapping("/{productId}")
    public StockResponse getStock(@PathVariable String productId) {
        return queryService.getStock(productId);
    }

    /** I3 - manual adjustment. Admin only. */
    @PostMapping("/{productId}/adjust")
    public StockResponse adjustStock(@PathVariable String productId,
                                     @Valid @RequestBody StockAdjustmentRequest request,
                                     Authentication authentication) {
        return commandService.adjustStock(productId, request.delta(), request.reason(), authentication.getName());
    }
}
