package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.request.StockAdjustmentRequest;
import com.ecommerce.inventoryservice.dto.response.LowStockAlertResponse;
import com.ecommerce.inventoryservice.dto.response.StockResponse;
import com.ecommerce.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Presentation layer for the Inventory Service. Performs basic request/response
 * plumbing only - no business logic - per specs/1-technical-architecture.md.
 * Endpoint shapes follow specs/4-api-endpoints.md and specs/openapi-contract.yaml.
 * Every @PathVariable below is explicitly named, even though the parent pom
 * sets the -parameters compiler flag project-wide - defense in depth against
 * the bug that broke product-service's tests earlier.
 * <p>
 * listActiveAlerts ("/alerts") is declared above getStock ("/{productId}") for
 * readability only - Spring MVC resolves the exact-path mapping over the
 * variable one regardless of declaration order, unlike SecurityConfig's
 * matcher list, where order is load-bearing.
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /** I4. */
    @GetMapping("/alerts")
    public ResponseEntity<List<LowStockAlertResponse>> listActiveAlerts() {
        return ResponseEntity.ok(inventoryService.listActiveAlerts());
    }

    /** I1. */
    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(@PathVariable(name = "productId") String productId) {
        return ResponseEntity.ok(inventoryService.getStock(productId));
    }

    /** I3. */
    @PostMapping("/{productId}/adjust")
    public ResponseEntity<StockResponse> adjustStock(
            Authentication authentication,
            @PathVariable(name = "productId") String productId,
            @Valid @RequestBody StockAdjustmentRequest request) {
        String adminUserId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(inventoryService.adjustStock(productId, request, adminUserId));
    }
}
