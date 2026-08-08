package com.valentin.inventoryservice.messaging.outbox;

import com.valentin.inventoryservice.db.OutboxEventRepository;
import com.valentin.inventoryservice.domain.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

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
    public void releaseStuckProcessingEvents(Instant threshold) {
        outboxEventRepository.releaseStuckProcessingEvents(threshold);
    }
}
