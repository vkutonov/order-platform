package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.OutboxEventRepository;
import com.valentin.inventoryservice.db.ProcessedEventsRepository;
import com.valentin.inventoryservice.domain.OutboxEventEntity;
import com.valentin.inventoryservice.dto.ReservationResult;
import com.valentin.inventoryservice.mapper.OrderCreatedEventMapper;
import com.valentin.inventoryservice.messaging.event.InventoryReservationFailedEvent;
import com.valentin.inventoryservice.messaging.event.InventoryReservedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreatedEventHandler {

    private static final String RESERVATION_AGGREGATE_TYPE = "Reservation";

    private final ReservationService reservationService;
    private final ProcessedEventsRepository processedEventsRepository;
    private final OrderCreatedEventMapper mapper;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void handle(OrderCreatedEvent event) {
        Instant now = clock.instant();

        int insert = processedEventsRepository.tryInsert(
                event.eventId(),
                event.eventType(),
                event.orderId(),
                now
        );

        if (insert == 0) {
            return;
        }

        ReservationResult result = reservationService.reserve(mapper.toCommand(event));

        UUID eventId = UUID.randomUUID();

        OutboxEventEntity outboxEvent;
        if (result.successful()) {
            InventoryReservedEvent reservedEvent = InventoryReservedEvent.of(
                    eventId,
                    result.orderId(),
                    result.reservationId(),
                    now
            );

            outboxEvent = OutboxEventEntity.create(
                    eventId,
                    RESERVATION_AGGREGATE_TYPE,
                    result.reservationId(),
                    reservedEvent.eventType(),
                    objectMapper.writeValueAsString(reservedEvent),
                    now
            );
        } else {
            InventoryReservationFailedEvent failedEvent = InventoryReservationFailedEvent.of(
                    eventId,
                    result.orderId(),
                    result.reservationId(),
                    result.failureCode(),
                    now
            );

            outboxEvent = OutboxEventEntity.create(
                    eventId,
                    RESERVATION_AGGREGATE_TYPE,
                    result.reservationId(),
                    failedEvent.eventType(),
                    objectMapper.writeValueAsString(failedEvent),
                    now
            );
        }

        outboxEventRepository.save(outboxEvent);

        log.info(
                "Outbox event created: eventId={}, aggregateType={}, aggregateId={}, eventType={}, status={}",
                outboxEvent.getId(),
                outboxEvent.getAggregateType(),
                outboxEvent.getAggregateId(),
                outboxEvent.getEventType(),
                outboxEvent.getStatus()
        );
    }
}
