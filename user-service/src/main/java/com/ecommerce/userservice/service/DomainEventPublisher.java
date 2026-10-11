package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Application-service collaborator that dispatches the domain events an aggregate collected while
 * executing a command — 6-cqrs-command-query.md §3.1 ("publishes domain events for read model
 * updates and cross-service integrations").
 *
 * <p>This service owns no read models and publishes no Kafka topics (see §8's integration event
 * catalog — User Service has no entries), so dispatch currently means recording the event. It is a
 * single seam: any future projection or topic is wired in here rather than in the aggregate.
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
