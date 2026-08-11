package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.ProcessedEventRepository;
import com.valentin.orderservice.domain.dictionary.ReservationFailureCode;
import com.valentin.orderservice.messaging.event.InventoryReservationFailedEvent;
import com.valentin.orderservice.messaging.event.InventoryReservedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryEventHandlerTest {

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RESERVATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T10:00:00Z");

    private ProcessedEventRepository processedEventRepository;
    private OrderCommandService commandService;
    private InventoryEventHandler handler;

    @BeforeEach
    void setUp() {
        processedEventRepository = mock(ProcessedEventRepository.class);
        commandService = mock(OrderCommandService.class);
        Clock clock = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
        handler = new InventoryEventHandler(processedEventRepository, commandService, clock);
    }

    @Test
    void handle_whenInventoryReserved_shouldAdvanceOrderToPayment() {
        InventoryReservedEvent event = reservedEvent();

        when(processedEventRepository.tryInsert(
                EVENT_ID,
                "InventoryReservedEvent",
                ORDER_ID,
                OCCURRED_AT
        )).thenReturn(1);

        handler.handle(event);

        verify(commandService).reserveInventory(ORDER_ID);
    }

    @Test
    void handle_whenInventoryReservationFailed_shouldCancelOrder() {
        InventoryReservationFailedEvent event = failedEvent();

        when(processedEventRepository.tryInsert(
                EVENT_ID,
                "InventoryReservationFailedEvent",
                ORDER_ID,
                OCCURRED_AT
        )).thenReturn(1);

        handler.handle(event);

        verify(commandService).inventoryReservationFailed(ORDER_ID, event.failureCode());
    }

    @Test
    void handle_whenEventAlreadyProcessed_shouldSkipOrderTransition() {
        InventoryReservedEvent event = reservedEvent();

        when(processedEventRepository.tryInsert(
                EVENT_ID,
                "InventoryReservedEvent",
                ORDER_ID,
                OCCURRED_AT
        )).thenReturn(0);

        handler.handle(event);

        verifyNoInteractions(commandService);
    }

    private InventoryReservedEvent reservedEvent() {
        return new InventoryReservedEvent(
                EVENT_ID,
                "InventoryReservedEvent",
                1,
                ORDER_ID,
                RESERVATION_ID,
                Map.of("source", "inventory-service"),
                OCCURRED_AT
        );
    }

    private InventoryReservationFailedEvent failedEvent() {
        return new InventoryReservationFailedEvent(
                EVENT_ID,
                "InventoryReservationFailedEvent",
                1,
                ORDER_ID,
                RESERVATION_ID,
                ReservationFailureCode.INSUFFICIENT_STOCK,
                Map.of("source", "inventory-service"),
                OCCURRED_AT
        );
    }
}
