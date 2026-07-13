package com.valentin.orderservice.logic.outbox;

import com.valentin.orderservice.domain.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class OutboxKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.order-events}")
    private String orderEventsTopic;

    public void publish(OutboxEventEntity event) throws Exception {
        kafkaTemplate.send(
                orderEventsTopic,
                event.getAggregateId().toString(),
                event.getPayload()
        ).get();
    }
}
