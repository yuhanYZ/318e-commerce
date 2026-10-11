package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches domain events that are not part of the event store — 6-cqrs-command-query.md
 * section 3.1. Inventory Service publishes no Kafka topics of its own beyond republishing stock
 * events for I4, so dispatch currently means recording the event.
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
