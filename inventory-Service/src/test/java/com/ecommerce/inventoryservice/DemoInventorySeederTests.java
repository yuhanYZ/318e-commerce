package com.ecommerce.inventoryservice;

import com.ecommerce.inventoryservice.domain.ProductId;
import com.ecommerce.inventoryservice.repository.InventoryReadModelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoInventorySeederTests {

    @Autowired
    private InventoryReadModelRepository inventoryReadModelRepository;

    @Test
    void seedsAvailableStockForTheFixedDemoProducts() {
        assertThat(inventoryReadModelRepository.findById(new ProductId("PRD-OAK-DESK")))
                .isPresent()
                .get()
                .satisfies(stock -> {
                    assertThat(stock.getQuantityAvailable()).isEqualTo(25);
                    assertThat(stock.getVersion()).isEqualTo(1);
                });
    }
}
