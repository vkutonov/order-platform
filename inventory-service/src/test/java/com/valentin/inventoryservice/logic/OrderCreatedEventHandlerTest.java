package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.ProcessedEventsRepository;
import com.valentin.inventoryservice.mapper.OrderCreatedEventMapper;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class OrderCreatedEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private ReservationService reservationService;
    private ProcessedEventsRepository processedEventsRepository;
    private OrderCreatedEventMapper mapper;
    private OrderCreatedEventHandler handler;

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationService.class);
        processedEventsRepository = mock(ProcessedEventsRepository.class);
        mapper = mock(OrderCreatedEventMapper.class);

        handler = new OrderCreatedEventHandler(
                reservationService,
                processedEventsRepository,
                mapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
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
        verifyNoInteractions(mapper, reservationService);
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
