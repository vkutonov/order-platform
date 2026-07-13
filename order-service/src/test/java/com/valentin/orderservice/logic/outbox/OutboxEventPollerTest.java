package com.valentin.orderservice.logic.outbox;

import com.valentin.orderservice.domain.OutboxEventEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxEventPollerTest {
    private final static Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private OutboxKafkaPublisher outboxKafkaPublisher;

    private Clock clock;

    private OutboxEventPoller outboxEventPoller;

    @BeforeEach
    void setUp () {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        outboxEventPoller = new OutboxEventPoller(
                outboxKafkaPublisher,
                outboxEventService,
                clock
        );

        ReflectionTestUtils.setField(outboxEventPoller, "processingTimeout", 60000L);
        ReflectionTestUtils.setField(outboxEventPoller, "batchSize", 50);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void pollAndPublish_shouldPublishClaimedEventsAndMarkPublished() throws Exception {

        Instant expectedThreshold = Instant.parse("2026-07-13T11:59:00Z");

        OutboxEventEntity event = OutboxEventEntity.create(
                "Order",
                UUID.randomUUID(),
                "OrderCreatedEvent",
                "{\"orderId\":\"test\"}",
                clock.instant()
        );

        when(outboxEventService.claimNewEvents(50))
                .thenReturn(List.of(event));

        outboxEventPoller.pollAndPublish();

        verify(outboxEventService).releaseStuckProcessingEvents(
                expectedThreshold
        );

        verify(outboxEventService).claimNewEvents(50);

        verify(outboxKafkaPublisher).publish(event);

        verify(outboxEventService).markPublished(event.getId(), NOW);

        verify(outboxEventService, never())
                .markNewAfterFailure(any(), any());
    }

    @Test
    void pollAndPublish_whenPublishingFails_shouldReturnEventToNew() throws Exception {
        OutboxEventEntity event = event();
        RuntimeException failure = new RuntimeException("Kafka is unavailable");

        when(outboxEventService.claimNewEvents(50))
                .thenReturn(List.of(event));
        doThrow(failure).when(outboxKafkaPublisher).publish(event);

        outboxEventPoller.pollAndPublish();

        verify(outboxEventService)
                .markNewAfterFailure(event.getId(), failure.getMessage());
        verify(outboxEventService, never())
                .markPublished(any(), any());
    }

    @Test
    void pollAndPublish_whenPublishingIsInterrupted_shouldReturnCurrentEventToNewAndStopBatch()
            throws Exception {
        OutboxEventEntity interruptedEvent = event();
        OutboxEventEntity nextEvent = event();

        when(outboxEventService.claimNewEvents(50))
                .thenReturn(List.of(interruptedEvent, nextEvent));
        doThrow(new InterruptedException("shutdown"))
                .when(outboxKafkaPublisher)
                .publish(interruptedEvent);

        outboxEventPoller.pollAndPublish();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(outboxEventService).markNewAfterFailure(
                interruptedEvent.getId(),
                "Publishing interrupted: shutdown"
        );
        verify(outboxKafkaPublisher, never()).publish(nextEvent);
        verify(outboxEventService, never())
                .markPublished(any(), any());
    }

    private OutboxEventEntity event() {
        return OutboxEventEntity.create(
                "Order",
                UUID.randomUUID(),
                "OrderCreatedEvent",
                "{\"orderId\":\"test\"}",
                clock.instant()
        );
    }
}
