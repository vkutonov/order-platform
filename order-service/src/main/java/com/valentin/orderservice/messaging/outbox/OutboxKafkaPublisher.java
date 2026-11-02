package com.valentin.orderservice.messaging.outbox;

import com.valentin.orderservice.domain.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


@Component
@RequiredArgsConstructor
public class OutboxKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.order-events}")
    private String orderEventsTopic;

    public CompletableFuture<SendResult<String, String>> publish(OutboxEventEntity event) {
        try {
            return kafkaTemplate.send(
                    orderEventsTopic,
                    event.getAggregateId().toString(),
                    event.getPayload()
            );
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
