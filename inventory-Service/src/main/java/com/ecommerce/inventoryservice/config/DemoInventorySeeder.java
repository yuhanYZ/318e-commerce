package com.ecommerce.inventoryservice.config;

import com.ecommerce.inventoryservice.domain.ProductId;
import com.ecommerce.inventoryservice.dto.StockAdjustmentRequest;
import com.ecommerce.inventoryservice.repository.StockEventRepository;
import com.ecommerce.inventoryservice.service.InventoryCommandService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates stock streams for Product Service's fixed demo catalogue at startup.
 *
 * <p>The event-store check makes the seed idempotent: a persisted inventory database keeps its
 * existing quantity, while the in-memory H2 database receives the same stock on every restart.
 */
@Component
public class DemoInventorySeeder implements ApplicationRunner {

    private static final int INITIAL_QUANTITY = 25;

    private static final List<String> PRODUCT_IDS = List.of(
            "PRD-AURORA-LAMP",
            "PRD-OAK-DESK",
            "PRD-WIRELESS-KEYBOARD",
            "PRD-ERGONOMIC-MOUSE",
            "PRD-INSULATED-BOTTLE",
            "PRD-WEEKEND-TOTE"
    );

    private final StockEventRepository stockEventRepository;
    private final InventoryCommandService inventoryCommandService;

    public DemoInventorySeeder(StockEventRepository stockEventRepository,
                               InventoryCommandService inventoryCommandService) {
        this.stockEventRepository = stockEventRepository;
        this.inventoryCommandService = inventoryCommandService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String productId : PRODUCT_IDS) {
            ProductId id = new ProductId(productId);
            if (stockEventRepository.findByAggregateIdOrderByAggregateVersionAsc(id).isEmpty()) {
                inventoryCommandService.adjustStock(productId,
                        new StockAdjustmentRequest(INITIAL_QUANTITY, "Initial demo stock"), "SYSTEM-SEED");
            }
        }
    }
}
