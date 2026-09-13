package com.ecommerce.inventoryservice.messaging;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;

/**
 * Kafka-profile publisher: sends stock level changes to {@value #DESTINATION}, consumed by the
 * {@code lowStockAlertJob} binding in this same service.
 *
 * <p>Sent only after the surrounding transaction commits. Sending earlier would let a rolled-back
 * stock change still produce an alert.
 */
@Component
@Profile("kafka")
public class StreamBridgeStockLevelChangedPublisher implements StockLevelChangedPublisher {

    static final String DESTINATION = "inventory.stock-level-changed";

    private final StreamBridge streamBridge;

    public StreamBridgeStockLevelChangedPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publish(StockLevelChanged change) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(change);
                }
            });
        } else {
            send(change);
        }
    }

    private void send(StockLevelChanged change) {
        Message<StockLevelChanged> message = MessageBuilder.withPayload(change)
                .setHeader(KafkaHeaders.KEY, change.productId().getBytes(StandardCharsets.UTF_8))
                .build();
        streamBridge.send(DESTINATION, message);
    }
}
