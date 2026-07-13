package com.valentin.orderservice.db;

import com.valentin.orderservice.domain.OutboxEventEntity;
import com.valentin.orderservice.domain.dictionary.OutboxEventStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OutboxEventRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-bookworm");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void claimNewEvents_shouldClaimOnlyNewEventsOrderedByCreatedAtAndLimited() {
        Instant createdAt = Instant.parse("2026-07-11T10:15:30Z");

        UUID publishedAggregateId = UUID.randomUUID();
        UUID firstAggregateId = UUID.randomUUID();
        UUID secondAggregateId = UUID.randomUUID();
        UUID thirdAggregateId = UUID.randomUUID();

        OutboxEventEntity publishedEvent = createEvent(publishedAggregateId, createdAt.minusSeconds(10));
        publishedEvent.markPublished(createdAt.minusSeconds(5));

        OutboxEventEntity firstNewEvent = createEvent(firstAggregateId, createdAt);
        firstNewEvent.markNewAfterFailure("previous publish failure");

        OutboxEventEntity secondNewEvent = createEvent(secondAggregateId, createdAt.plusSeconds(1));
        OutboxEventEntity thirdNewEvent = createEvent(thirdAggregateId, createdAt.plusSeconds(2));

        outboxEventRepository.saveAll(List.of(
                thirdNewEvent,
                publishedEvent,
                secondNewEvent,
                firstNewEvent
        ));

        entityManager.flush();
        entityManager.clear();

        List<OutboxEventEntity> result = outboxEventRepository.claimNewEvents(2);

        assertThat(result)
                .hasSize(2)
                .extracting(OutboxEventEntity::getAggregateId)
                .containsExactlyInAnyOrder(firstAggregateId, secondAggregateId);

        assertThat(result)
                .extracting(OutboxEventEntity::getStatus)
                .containsOnly(OutboxEventStatus.PROCESSING);

        assertThat(result)
                .allSatisfy(event -> {
                    assertThat(event.getProcessedAt()).isNotNull();
                    assertThat(event.getErrorMessage()).isNull();
                });

        entityManager.clear();

        List<OutboxEventEntity> allEvents = outboxEventRepository.findAll();

        assertThat(allEvents)
                .filteredOn(event -> event.getAggregateId().equals(thirdAggregateId))
                .singleElement()
                .extracting(OutboxEventEntity::getStatus)
                .isEqualTo(OutboxEventStatus.NEW);

        assertThat(allEvents)
                .filteredOn(event -> event.getAggregateId().equals(publishedAggregateId))
                .singleElement()
                .extracting(OutboxEventEntity::getStatus)
                .isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void releaseStuckProcessingEvents_shouldReleaseOnlyProcessingEventsOlderThanThreshold() {
        Instant threshold = Instant.parse("2026-07-12T10:15:30Z");

        OutboxEventEntity oldProcessingEvent = createEvent(UUID.randomUUID(), threshold.minusSeconds(120));
        OutboxEventEntity freshProcessingEvent = createEvent(UUID.randomUUID(), threshold.minusSeconds(30));
        OutboxEventEntity newEvent = createEvent(UUID.randomUUID(), threshold.minusSeconds(60));
        outboxEventRepository.saveAll(List.of(oldProcessingEvent, freshProcessingEvent, newEvent));

        entityManager.flush();
        setProcessing(oldProcessingEvent.getId(), threshold.minusSeconds(1));
        setProcessing(freshProcessingEvent.getId(), threshold.plusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        int released = outboxEventRepository.releaseStuckProcessingEvents(threshold);

        assertThat(released).isEqualTo(1);

        entityManager.clear();

        OutboxEventEntity releasedEvent = outboxEventRepository.findById(oldProcessingEvent.getId()).orElseThrow();
        assertThat(releasedEvent.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(releasedEvent.getProcessedAt()).isNull();
        assertThat(releasedEvent.getErrorMessage()).isEqualTo("Released stuck PROCESSING event");

        OutboxEventEntity stillProcessingEvent = outboxEventRepository.findById(freshProcessingEvent.getId()).orElseThrow();
        assertThat(stillProcessingEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(stillProcessingEvent.getProcessedAt()).isEqualTo(threshold.plusSeconds(1));

        OutboxEventEntity unchangedNewEvent = outboxEventRepository.findById(newEvent.getId()).orElseThrow();
        assertThat(unchangedNewEvent.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(unchangedNewEvent.getProcessedAt()).isNull();
    }

    private void setProcessing(UUID eventId, Instant processedAt) {
        entityManager.createNativeQuery("""
                UPDATE outbox_events
                SET status = 'PROCESSING', processed_at = :processedAt
                WHERE id = :eventId
                """)
                .setParameter("eventId", eventId)
                .setParameter("processedAt", processedAt)
                .executeUpdate();
    }

    private OutboxEventEntity createEvent(UUID aggregateId, Instant createdAt) {
        String payload = """
                {
                  "eventId": "%s",
                  "orderId": "%s",
                  "context": {
                    "source": "order-service"
                  },
                  "occurredAt": "%s"
                }
                """.formatted(UUID.randomUUID(), aggregateId, createdAt);

        return OutboxEventEntity.create(
                "Order",
                aggregateId,
                "OrderCreatedEvent",
                payload,
                createdAt
        );
    }
}
