package com.ecommerce.inventoryservice.analytics;

import com.ecommerce.inventoryservice.domain.LowStockAlert;
import com.ecommerce.inventoryservice.repository.LowStockAlertRepository;
import com.ecommerce.inventoryservice.service.DomainEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the I4 topology's threshold verdicts into the {@code LowStockAlert} lifecycle: opens an
 * alert when a product is below the threshold and has none open, resolves it when the product
 * clears the threshold again. The verdict itself is computed in the topology; only the stateful
 * open/resolve transition lives here, because it depends on what is already in the store.
 */
@Component
public class LowStockAlertSinkWriter implements LowStockAlertSink {

    private final LowStockAlertRepository lowStockAlertRepository;
    private final DomainEventPublisher domainEventPublisher;

    public LowStockAlertSinkWriter(LowStockAlertRepository lowStockAlertRepository,
                                    DomainEventPublisher domainEventPublisher) {
        this.lowStockAlertRepository = lowStockAlertRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    @Transactional
    public void apply(StockAlertState state) {
        Instant occurredAt = Instant.ofEpochMilli(state.occurredAtEpochMilli());
        Optional<LowStockAlert> openAlert =
                lowStockAlertRepository.findByStockItemIdAndResolvedAtIsNull(state.productId());

        if (state.belowThreshold() && openAlert.isEmpty()) {
            LowStockAlert alert = new LowStockAlert(UUID.randomUUID().toString(), state.productId(), occurredAt);
            lowStockAlertRepository.save(alert);
            domainEventPublisher.publish(alert.pullDomainEvents());
        } else if (!state.belowThreshold() && openAlert.isPresent()) {
            LowStockAlert alert = openAlert.get();
            alert.resolve(occurredAt);
            lowStockAlertRepository.save(alert);
            domainEventPublisher.publish(alert.pullDomainEvents());
        }
    }
}
