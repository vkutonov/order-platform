package com.valentin.inventoryservice.messaging.outbox;

import com.valentin.inventoryservice.domain.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Component
public class OutboxKafkaPublisher {

    @Value("${app.kafka.topics.inventory-events}")
    private String inventoryTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<SendResult<String, String>> publish(OutboxEventEntity event) {
        try {
            return kafkaTemplate.send(
                    inventoryTopic,
                    event.getAggregateId().toString(),
                    event.getPayload()
            );
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
