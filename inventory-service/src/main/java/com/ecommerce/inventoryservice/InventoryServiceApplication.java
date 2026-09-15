package com.ecommerce.inventoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Inventory Service (I1-I5) - specs/iter2/2-user-stories-inventory-service-v2.md.
 *
 * <p>Owns stock levels. {@code StockItem} is the only event-sourced aggregate in the system
 * (5-event-sourcing.md); I1 reads from the {@code inventory_read_model} projection, per the CQRS
 * split in 6-cqrs-command-query.md. Runs on port 8084.
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
