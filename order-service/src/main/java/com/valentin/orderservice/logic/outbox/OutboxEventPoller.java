package com.valentin.orderservice.logic.outbox;

import com.valentin.orderservice.domain.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventPoller {

    private final OutboxKafkaPublisher outboxKafkaPublisher;
    private final OutboxEventService outboxEventService;

    @Value("${app.outbox.polling.batch-size}")
    private int batchSize;

    public void pollAndPublish() {
        List<OutboxEventEntity> events = outboxEventService.claimNewEvents(batchSize);

        if (events.isEmpty()) {
            log.debug("No outbox events to publish");
            return;
        }

        log.info("Claimed outbox events for publishing: count={}", events.size());

        for (OutboxEventEntity event : events) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEventEntity event) {
        try {
            outboxKafkaPublisher.publish(event);

            outboxEventService.markPublished(event.getId(), Instant.now());

            log.info(
                    "Outbox event published: eventId={}, aggregateId={}, eventType={}",
                    event.getId(),
                    event.getAggregateId(),
                    event.getEventType()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            outboxEventService.markNewAfterFailure(
                    event.getId(),
                    "Publishing interrupted: " + ex.getMessage()
            );

            log.warn(
                    "Outbox event publishing interrupted: eventId={}, aggregateId={}, eventType={}",
                    event.getId(),
                    event.getAggregateId(),
                    event.getEventType(),
                    ex
            );
        } catch (Exception ex) {
            outboxEventService.markNewAfterFailure(event.getId(), ex.getMessage());

            log.warn(
                    "Outbox event publishing failed: eventId={}, aggregateId={}, eventType={}",
                    event.getId(),
                    event.getAggregateId(),
                    event.getEventType(),
                    ex
            );
        }
    }

}
