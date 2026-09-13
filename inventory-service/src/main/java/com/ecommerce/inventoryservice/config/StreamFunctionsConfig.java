package com.ecommerce.inventoryservice.config;

import com.ecommerce.inventoryservice.domain.InventoryPolicyService;
import com.ecommerce.inventoryservice.messaging.StockLevelChanged;
import com.ecommerce.inventoryservice.service.LowStockAlertEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * The I4 real-time stream job, plus the domain service it depends on.
 *
 * <p>{@code lowStockAlertJob} is a standard Spring Cloud Stream functional bean. It exists in every
 * profile. Without the kafka profile it is called directly by InProcessStockLevelChangedPublisher;
 * with it, Spring Cloud Stream binds it to inventory.stock-level-changed (see application.yml).
 */
@Configuration
public class StreamFunctionsConfig {

    @Bean
    public Consumer<StockLevelChanged> lowStockAlertJob(LowStockAlertEvaluator evaluator) {
        return evaluator::evaluate;
    }

    /** Wires the plain-Java domain service with the global threshold from configuration. */
    @Bean
    public InventoryPolicyService inventoryPolicyService(
            @Value("${app.inventory.low-stock-threshold}") int lowStockThreshold) {
        return new InventoryPolicyService(lowStockThreshold);
    }
}
