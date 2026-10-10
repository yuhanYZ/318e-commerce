package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches the internal domain events an aggregate collected while executing a command —
 * 6-cqrs-command-query.md §3.1.
 *
 * <p>Cross-service delivery is handled separately and deliberately by the command service, which
 * translates `PaymentSucceeded` / `PaymentFailed` / `RefundProcessed` into Kafka integration events
 * through StreamBridge (§8). This publisher covers the internal-only events.
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
