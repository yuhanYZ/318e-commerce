package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.InventoryPolicyService;
import com.ecommerce.inventoryservice.domain.LowStockAlert;
import com.ecommerce.inventoryservice.domain.Quantity;
import com.ecommerce.inventoryservice.messaging.StockLevelChanged;
import com.ecommerce.inventoryservice.repository.LowStockAlertRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * I4 low-stock evaluation, run by the {@code lowStockAlertJob} Spring Cloud Stream function.
 *
 * <p>Opens an alert when stock drops below the threshold and none is open; resolves the open alert
 * once stock is back at or above it. Checking for an open alert first enforces the aggregate
 * invariant of at most one unresolved alert per stock item, which also satisfies I4 iii: no new
 * alert until stock recovers and drops again.
 *
 * <p>Joins the caller's transaction when invoked in-process; starts its own when delivered from Kafka.
 */
@Service
@Transactional
public class LowStockAlertEvaluator {

    private final InventoryPolicyService policyService;
    private final LowStockAlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LowStockAlertEvaluator(InventoryPolicyService policyService,
                                  LowStockAlertRepository alertRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.policyService = policyService;
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
    }

    public void evaluate(StockLevelChanged change) {
        boolean below = policyService.isBelowThreshold(new Quantity(change.quantityAvailable()));
        Optional<LowStockAlert> openAlert = alertRepository.findByStockItemIdAndResolvedAtIsNull(change.productId());

        if (below && openAlert.isEmpty()) {
            LowStockAlert alert = LowStockAlert.trigger(change.productId(), change.occurredAt());
            alertRepository.save(alert);
            alert.pullDomainEvents().forEach(eventPublisher::publishEvent);
        } else if (!below && openAlert.isPresent()) {
            LowStockAlert alert = openAlert.get();
            alert.resolve(change.occurredAt());
            alertRepository.save(alert);
            alert.pullDomainEvents().forEach(eventPublisher::publishEvent);
        }
    }
}
