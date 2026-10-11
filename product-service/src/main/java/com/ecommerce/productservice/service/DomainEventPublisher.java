package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches the domain events an aggregate collected while executing a command —
 * 6-cqrs-command-query.md §3.1.
 *
 * <p>Product Service owns no read models and publishes no Kafka topics (§8's catalog has no
 * Product entries), so dispatch currently means recording the event. Single seam for any future
 * projection or topic.
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
