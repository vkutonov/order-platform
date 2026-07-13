package com.valentin.orderservice.logic.outbox;

import com.valentin.orderservice.domain.OutboxEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "orderEventsTopic", "order.events");
    }

    @Test
    void publish_shouldSendPayloadToConfiguredTopicWithAggregateIdAsKey() throws Exception {
        OutboxEventEntity event = event();
        when(kafkaTemplate.send("order.events", event.getAggregateId().toString(), event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        verify(kafkaTemplate).send(
                "order.events",
                event.getAggregateId().toString(),
                event.getPayload()
        );
    }

    @Test
    void publish_shouldPropagateBrokerAcknowledgementFailure() {
        OutboxEventEntity event = event();
        IllegalStateException sendFailure = new IllegalStateException("Kafka is unavailable");
        when(kafkaTemplate.send("order.events", event.getAggregateId().toString(), event.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(sendFailure));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(ExecutionException.class)
                .hasCause(sendFailure);
    }

    private OutboxEventEntity event() {
        return OutboxEventEntity.create(
                "Order",
                UUID.randomUUID(),
                "OrderCreatedEvent",
                "{\"orderId\":\"test\"}",
                Instant.parse("2026-07-12T10:15:30Z")
        );
    }
}
