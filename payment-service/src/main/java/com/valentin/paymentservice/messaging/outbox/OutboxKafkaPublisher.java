package com.valentin.paymentservice.messaging.outbox;

import com.valentin.paymentservice.domain.OutboxEventEntity;
import com.valentin.paymentservice.messaging.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.payment-events}")
    private String paymentEventsTopic;

    @Value("${app.kafka.topics.payment-commands}")
    private String paymentCommandsTopic;

    public void publish(OutboxEventEntity event) throws Exception {
        String topic = switch (event.getEventType()) {
            case CreatePaymentRequested.TYPE, RefundPaymentRequested.TYPE -> paymentCommandsTopic;
            case PaymentSessionCreated.TYPE, PaymentSucceeded.TYPE, PaymentFailed.TYPE,
                    PaymentRefunded.TYPE, PaymentRefundFailed.TYPE -> paymentEventsTopic;
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + event.getEventType());
        };
        kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
    }
}
