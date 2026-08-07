package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.OutboxEventRepository;
import com.valentin.inventoryservice.db.ProcessedEventsRepository;
import com.valentin.inventoryservice.domain.OutboxEventEntity;
import com.valentin.inventoryservice.domain.dictionary.OutboxEventStatus;
import com.valentin.inventoryservice.domain.dictionary.ReservationFailureCode;
import com.valentin.inventoryservice.dto.CreateReservationCommand;
import com.valentin.inventoryservice.dto.ReservationItemCommand;
import com.valentin.inventoryservice.dto.ReservationResult;
import com.valentin.inventoryservice.mapper.OrderCreatedEventMapper;
import com.valentin.inventoryservice.messaging.event.InventoryReservationFailedEvent;
import com.valentin.inventoryservice.messaging.event.InventoryReservedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderCreatedEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private ReservationService reservationService;
    private ProcessedEventsRepository processedEventsRepository;
    private OrderCreatedEventMapper eventMapper;
    private OrderCreatedEventHandler handler;
    private OutboxEventRepository outboxEventRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationService.class);
        processedEventsRepository = mock(ProcessedEventsRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        eventMapper = mock(OrderCreatedEventMapper.class);
        objectMapper = mock(ObjectMapper.class);

        handler = new OrderCreatedEventHandler(
                reservationService,
                processedEventsRepository,
                eventMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                objectMapper,
                outboxEventRepository
        );
    }

    @Test
    void handle_whenEventAlreadyProcessed_shouldSkipReservation() {
        OrderCreatedEvent event = event();

        when(processedEventsRepository.tryInsert(
                EVENT_ID,
                "OrderCreatedEvent",
                ORDER_ID,
                NOW
        )).thenReturn(0);

        handler.handle(event);

        verify(processedEventsRepository).tryInsert(
                EVENT_ID,
                "OrderCreatedEvent",
                ORDER_ID,
                NOW
        );
        verifyNoInteractions(
                eventMapper,
                reservationService,
                objectMapper,
                outboxEventRepository
        );
    }

    @Test
    void handle_whenReservationSucceeds_shouldSaveReservedEventToOutbox() {
        OrderCreatedEvent event = event();
        CreateReservationCommand command = command();
        String payload = "{\"eventType\":\"InventoryReservedEvent\"}";

        when(processedEventsRepository.tryInsert(EVENT_ID, "OrderCreatedEvent", ORDER_ID, NOW))
                .thenReturn(1);
        when(eventMapper.toCommand(event)).thenReturn(command);
        when(reservationService.reserve(command)).thenReturn(ReservationResult.reserved(
                RESERVATION_ID,
                ORDER_ID,
                NOW.plusSeconds(3600)
        ));
        when(objectMapper.writeValueAsString(any(InventoryReservedEvent.class)))
                .thenReturn(payload);

        handler.handle(event);

        ArgumentCaptor<InventoryReservedEvent> resultEventCaptor =
                ArgumentCaptor.forClass(InventoryReservedEvent.class);
        ArgumentCaptor<OutboxEventEntity> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);

        verify(objectMapper).writeValueAsString(resultEventCaptor.capture());
        verify(outboxEventRepository).save(outboxCaptor.capture());

        InventoryReservedEvent resultEvent = resultEventCaptor.getValue();
        OutboxEventEntity outboxEvent = outboxCaptor.getValue();

        assertThat(resultEvent.eventId()).isEqualTo(outboxEvent.getId());
        assertThat(resultEvent.eventType()).isEqualTo(InventoryReservedEvent.TYPE);
        assertThat(resultEvent.eventVersion()).isEqualTo(InventoryReservedEvent.VERSION);
        assertThat(resultEvent.orderId()).isEqualTo(ORDER_ID);
        assertThat(resultEvent.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(resultEvent.occurredAt()).isEqualTo(NOW);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("Reservation");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(RESERVATION_ID);
        assertThat(outboxEvent.getEventType()).isEqualTo(InventoryReservedEvent.TYPE);
        assertThat(outboxEvent.getPayload()).isEqualTo(payload);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(outboxEvent.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void handle_whenReservationFails_shouldSaveFailedEventToOutbox() {
        OrderCreatedEvent event = event();
        CreateReservationCommand command = command();
        String payload = "{\"eventType\":\"InventoryReservationFailedEvent\"}";

        when(processedEventsRepository.tryInsert(EVENT_ID, "OrderCreatedEvent", ORDER_ID, NOW))
                .thenReturn(1);
        when(eventMapper.toCommand(event)).thenReturn(command);
        when(reservationService.reserve(command)).thenReturn(ReservationResult.failed(
                RESERVATION_ID,
                ORDER_ID,
                ReservationFailureCode.INSUFFICIENT_STOCK
        ));
        when(objectMapper.writeValueAsString(any(InventoryReservationFailedEvent.class)))
                .thenReturn(payload);

        handler.handle(event);

        ArgumentCaptor<InventoryReservationFailedEvent> resultEventCaptor =
                ArgumentCaptor.forClass(InventoryReservationFailedEvent.class);
        ArgumentCaptor<OutboxEventEntity> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);

        verify(objectMapper).writeValueAsString(resultEventCaptor.capture());
        verify(outboxEventRepository).save(outboxCaptor.capture());

        InventoryReservationFailedEvent resultEvent = resultEventCaptor.getValue();
        OutboxEventEntity outboxEvent = outboxCaptor.getValue();

        assertThat(resultEvent.eventId()).isEqualTo(outboxEvent.getId());
        assertThat(resultEvent.eventType()).isEqualTo(InventoryReservationFailedEvent.TYPE);
        assertThat(resultEvent.eventVersion()).isEqualTo(InventoryReservationFailedEvent.VERSION);
        assertThat(resultEvent.orderId()).isEqualTo(ORDER_ID);
        assertThat(resultEvent.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(resultEvent.failureCode()).isEqualTo(ReservationFailureCode.INSUFFICIENT_STOCK);
        assertThat(resultEvent.occurredAt()).isEqualTo(NOW);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("Reservation");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(RESERVATION_ID);
        assertThat(outboxEvent.getEventType()).isEqualTo(InventoryReservationFailedEvent.TYPE);
        assertThat(outboxEvent.getPayload()).isEqualTo(payload);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(outboxEvent.getCreatedAt()).isEqualTo(NOW);
    }

    private CreateReservationCommand command() {
        return new CreateReservationCommand(
                ORDER_ID,
                USER_ID,
                List.of(new ReservationItemCommand(PRODUCT_ID, 1))
        );
    }

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent(
                EVENT_ID,
                "OrderCreatedEvent",
                1,
                ORDER_ID,
                USER_ID,
                List.of(new OrderCreatedItem(
                        PRODUCT_ID,
                        "Keyboard",
                        new BigDecimal("5000.00"),
                        "RUB",
                        1
                )),
                Map.of("source", "order-service"),
                NOW
        );
    }
}
