package com.valentin.paymentservice.messaging.outbox;

import com.valentin.paymentservice.domain.OutboxEventEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static java.lang.Thread.currentThread;

@Service
@Slf4j
public class OutboxEventPoller {

    private final OutboxKafkaPublisher outboxKafkaPublisher;
    private final OutboxEventService outboxEventService;
    private final Executor outboxExecutor;

    @Value("${app.outbox.polling.batch-size}")
    private int batchSize;

    @Value("${app.outbox.polling.processing-timeout-ms}")
    private long processingTimeout;

    private final Clock clock;

    public OutboxEventPoller(
            OutboxKafkaPublisher outboxKafkaPublisher,
            OutboxEventService outboxEventService,
            Clock clock,
            @Qualifier("outboxExecutor") Executor outboxExecutor
    ) {
        this.outboxKafkaPublisher = outboxKafkaPublisher;
        this.outboxEventService = outboxEventService;
        this.clock = clock;
        this.outboxExecutor = outboxExecutor;
    }

    public void pollAndPublish() {
        if (currentThread().isInterrupted()) {
            return;
        }

        Instant threshold = clock.instant().minusMillis(processingTimeout);

        int recovered = outboxEventService.releaseStuckProcessingEvents(threshold);
        if (recovered > 0) {
            log.warn("Stuck outbox events returned for retry: count={}, threshold={}", recovered, threshold);
        }

        List<OutboxEventEntity> events = outboxEventService.claimNewEvents(batchSize);

        if (events.isEmpty()) {
            log.debug("No outbox events to publish");
            return;
        }

        log.debug("Claimed outbox events for publishing: count={}", events.size());

        List<CompletableFuture<?>> futures = new ArrayList<>(events.size());
        for (OutboxEventEntity event : events) {
            futures.add(publishEvent(event));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private CompletableFuture<?> publishEvent(OutboxEventEntity event) {
        return outboxKafkaPublisher.publish(event)
                .handleAsync((result, exception) -> {
                    if (exception == null) {
                        outboxEventService.markPublished(event.getId(), clock.instant());
                        log.info(
                                "Outbox event published: eventId={}, eventType={}, aggregateId={}, topic={}, partition={}, offset={}",
                                event.getId(), event.getEventType(), event.getAggregateId(),
                                result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()
                        );
                    } else {
                        log.warn("Outbox event publishing failed: eventId={}, aggregateId={}, exceptionMessage={}",
                                event.getId(), event.getAggregateId(), exception.getMessage());
                        outboxEventService.markNewAfterFailure(event.getId(), exception.getMessage());
                    }
                    return null;
                }, outboxExecutor);
    }
}
