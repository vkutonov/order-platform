package com.valentin.paymentservice.messaging.outbox;

import com.valentin.paymentservice.domain.OutboxEventEntity;
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

    @Value("${app.kafka.topics.payment-events}")
    private String paymentEventsTopic;

    public CompletableFuture<SendResult<String, String>> publish(OutboxEventEntity event) {
        try {
            return kafkaTemplate.send(paymentEventsTopic, event.getAggregateId().toString(), event.getPayload());
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
