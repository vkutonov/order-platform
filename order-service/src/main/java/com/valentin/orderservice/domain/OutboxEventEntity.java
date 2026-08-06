package com.valentin.orderservice.domain;

import com.valentin.orderservice.domain.dictionary.OutboxEventStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "error_message")
    private String errorMessage;


    public static OutboxEventEntity create(
            UUID eventId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant createdAt
    ) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.id = eventId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.status = OutboxEventStatus.NEW;
        event.createdAt = createdAt;
        return event;
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.errorMessage = null;
    }

    public void markNewAfterFailure(String errorMessage) {
        this.status = OutboxEventStatus.NEW;
        this.errorMessage = errorMessage;
    }
}
