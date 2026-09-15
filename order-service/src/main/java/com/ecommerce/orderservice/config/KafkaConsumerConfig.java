package com.ecommerce.orderservice.config;

import com.ecommerce.orderservice.integration.PaymentConfirmedEvent;
import com.ecommerce.orderservice.integration.PaymentFailedEvent;
import com.ecommerce.orderservice.service.OrderCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.function.Consumer;

/**
 * O5 — Kafka consumer bindings for PaymentConfirmedEvent/PaymentFailedEvent
 * (specs/iter2/6-cqrs-command-query.md §8). Bean names here must match
 * {@code spring.cloud.function.definition} in application.yml's "kafka"-profile document exactly.
 *
 * <p><b>Active only under the "kafka" Spring profile</b> — see that same application.yml document
 * (guarded by {@code spring.config.activate.on-profile: kafka}) and the class-level {@code @Profile}
 * below. No contract test activates this profile, so during {@code mvn test} this configuration
 * class (and its beans) never exist in the Spring context at all — zero risk of Spring Cloud
 * Stream's Kafka binder trying to bind against a broker that isn't running during the test suite.
 * Run the app with {@code --spring.profiles.active=kafka} (or the equivalent docker-compose env
 * var) against a real broker (see the repo's {@code docker-compose.yml}) to activate this for real.
 */
@Configuration
@Profile("kafka")
public class KafkaConsumerConfig {

    @Bean
    public Consumer<PaymentConfirmedEvent> updateOrderOnPaymentConfirmed(OrderCommandService commandService) {
        return event -> commandService.updateOrderOnPaymentConfirmed(event.orderId());
    }

    @Bean
    public Consumer<PaymentFailedEvent> updateOrderOnPaymentFailed(OrderCommandService commandService) {
        return event -> commandService.updateOrderOnPaymentFailed(event.orderId());
    }
}
