package com.valentin.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;


    public static ProcessedEventEntity create(
            UUID eventId,
            String eventType,
            UUID aggregateId,
            Instant processedAt
    ) {
        ProcessedEventEntity processedEvent = new ProcessedEventEntity();

        processedEvent.eventId = eventId;
        processedEvent.eventType = eventType;
        processedEvent.aggregateId = aggregateId;
        processedEvent.processedAt = processedAt;

        return processedEvent;
    }
}
