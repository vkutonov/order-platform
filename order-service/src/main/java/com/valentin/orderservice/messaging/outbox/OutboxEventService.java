package com.valentin.orderservice.messaging.outbox;

import com.valentin.orderservice.db.OutboxEventRepository;
import com.valentin.orderservice.domain.OutboxEventEntity;
import com.valentin.orderservice.messaging.event.OrderLifecycleEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueue(OrderLifecycleEvent event) {
        outboxEventRepository.save(OutboxEventEntity.create(event.eventId(), "Order", event.orderId(),
                event.eventType(), objectMapper.writeValueAsString(event), event.occurredAt()));
    }

    @Transactional
    public List<OutboxEventEntity> claimNewEvents(int limit) {
        return outboxEventRepository.claimNewEvents(limit);
    }

    @Transactional
    public void markPublished(UUID eventId, Instant publishedAt) {
        OutboxEventEntity event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));

        event.markPublished(publishedAt);
    }

    @Transactional
    public void markNewAfterFailure(UUID eventId, String errorMessage) {
        OutboxEventEntity event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));

        event.markNewAfterFailure(errorMessage);
    }

    @Transactional
    public int releaseStuckProcessingEvents(Instant threshold) {
        return outboxEventRepository.releaseStuckProcessingEvents(threshold);
    }
}
