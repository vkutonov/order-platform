package com.valentin.inventoryservice.messaging.outbox;

import com.valentin.inventoryservice.domain.OutboxEventEntity;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPollerTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    @Mock
    private OutboxKafkaPublisher outboxKafkaPublisher;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private SendResult<String, String> sendResult;

    @Mock
    private RecordMetadata recordMetadata;

    private OutboxEventPoller outboxEventPoller;

    @BeforeEach
    void setUp() {
        outboxEventPoller = new OutboxEventPoller(
                outboxKafkaPublisher,
                outboxEventService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ReflectionTestUtils.setField(outboxEventPoller, "processingTimeout", 60000L);
        ReflectionTestUtils.setField(outboxEventPoller, "batchSize", 50);
    }

    @Test
    void pollAndPublish_whenPublishingSucceeds_shouldMarkEventPublished() {
        OutboxEventEntity event = event();
        CompletableFuture<SendResult<String, String>> publishFuture = new CompletableFuture<>();

        when(outboxEventService.claimNewEvents(50)).thenReturn(List.of(event));
        when(outboxKafkaPublisher.publish(event)).thenReturn(publishFuture);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.topic()).thenReturn("inventory.events");

        CompletableFuture<Void> pollingFuture = CompletableFuture.runAsync(
                outboxEventPoller::pollAndPublish
        );

        verify(outboxKafkaPublisher, timeout(1000)).publish(event);
        verify(outboxEventService, never()).markPublished(any(), any());

        publishFuture.complete(sendResult);
        pollingFuture.join();

        verify(outboxEventService).releaseStuckProcessingEvents(NOW.minusSeconds(60));
        verify(outboxEventService).markPublished(event.getId(), NOW);
        verify(outboxEventService, never()).markNewAfterFailure(any(), any());
    }

    @Test
    void pollAndPublish_whenPublishingFails_shouldReturnEventToNew() {
        OutboxEventEntity event = event();
        RuntimeException failure = new RuntimeException("Kafka is unavailable");

        when(outboxEventService.claimNewEvents(50)).thenReturn(List.of(event));
        when(outboxKafkaPublisher.publish(event))
                .thenReturn(CompletableFuture.failedFuture(failure));

        outboxEventPoller.pollAndPublish();

        verify(outboxEventService)
                .markNewAfterFailure(event.getId(), failure.getMessage());
        verify(outboxEventService, never()).markPublished(any(), any());
    }

    private OutboxEventEntity event() {
        return OutboxEventEntity.create(
                UUID.randomUUID(),
                "Order",
                UUID.randomUUID(),
                "InventoryReservedEvent",
                "{\"orderId\":\"test\"}",
                NOW
        );
    }
}
