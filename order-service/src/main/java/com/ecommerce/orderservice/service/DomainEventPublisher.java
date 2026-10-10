package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches the internal domain events an aggregate collected while executing a command —
 * 6-cqrs-command-query.md §3.1.
 *
 * <p>Cross-service delivery is handled separately and deliberately: the command service translates
 * `OrderPlaced` / `OrderCancelled` into Kafka integration events via StreamBridge (§8). `OrderPaid`
 * and `OrderPaymentFailed` have no consumer, so recording them is their whole lifecycle.
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    public void publish(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            log.info("Domain event: {}", event);
        }
    }
}
