package com.valentin.paymentservice.messaging.outbox;

import com.valentin.paymentservice.db.OutboxEventRepository;
import com.valentin.paymentservice.domain.OutboxEventEntity;
import com.valentin.paymentservice.messaging.event.PaymentMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(PaymentMessage message) {
        outboxEventRepository.save(OutboxEventEntity.create(
                message.eventId(), "Order", message.orderId(), message.eventType(),
                objectMapper.writeValueAsString(message), message.occurredAt()
        ));
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
