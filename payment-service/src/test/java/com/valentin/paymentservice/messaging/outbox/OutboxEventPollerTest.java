package com.valentin.paymentservice.messaging.outbox;

import com.valentin.paymentservice.domain.OutboxEventEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPollerTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxEventPoller poller;

    @BeforeEach
    void setUp() {
        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "paymentCommandsTopic", "payment.commands");
        ReflectionTestUtils.setField(publisher, "paymentEventsTopic", "payment.events");
        poller = new OutboxEventPoller(publisher, outboxEventService, Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(poller, "processingTimeout", 60000L);
        ReflectionTestUtils.setField(poller, "batchSize", 50);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void failedDelivery_isRetriedAndMarkedPublishedOnlyAfterBrokerAcknowledgement() throws Exception {
        OutboxEventEntity event = event();
        CountDownLatch sentAgain = new CountDownLatch(1);
        CompletableFuture<SendResult<String, String>> acknowledgement = new CompletableFuture<>();
        when(outboxEventService.claimNewEvents(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send("payment.events", event.getAggregateId().toString(), event.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")))
                .thenAnswer(invocation -> {
                    sentAgain.countDown();
                    return acknowledgement;
                });

        poller.pollAndPublish();

        verify(outboxEventService).markNewAfterFailure(eq(event.getId()), contains("Kafka unavailable"));
        verify(outboxEventService, never()).markPublished(any(), any());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var retry = executor.submit(poller::pollAndPublish);
            try {
                assertThat(sentAgain.await(5, TimeUnit.SECONDS)).isTrue();
                verify(outboxEventService, never()).markPublished(any(), any());
            } finally {
                acknowledgement.complete(null);
            }
            retry.get(5, TimeUnit.SECONDS);
        }

        verify(outboxEventService).markPublished(event.getId(), NOW);
        verify(outboxEventService, times(2)).releaseStuckProcessingEvents(NOW.minusSeconds(60));
        verify(kafkaTemplate, times(2)).send(
                "payment.events", event.getAggregateId().toString(), event.getPayload());
    }

    @Test
    void shutdownDuringDelivery_returnsCurrentEventToOutboxAndStopsBatch() {
        OutboxEventEntity current = event();
        OutboxEventEntity next = event();
        when(outboxEventService.claimNewEvents(50)).thenReturn(List.of(current, next));
        when(kafkaTemplate.send("payment.events", current.getAggregateId().toString(), current.getPayload()))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt();
                    return new CompletableFuture<SendResult<String, String>>();
                });

        poller.pollAndPublish();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(outboxEventService).markNewAfterFailure(eq(current.getId()), startsWith("Publishing interrupted:"));
        verify(outboxEventService, never()).markPublished(any(), any());
        verify(kafkaTemplate, never()).send(
                "payment.events", next.getAggregateId().toString(), next.getPayload());
    }

    private OutboxEventEntity event() {
        return OutboxEventEntity.create(UUID.randomUUID(), "Order", UUID.randomUUID(),
                "PaymentSucceeded", "{}", NOW);
    }
}
