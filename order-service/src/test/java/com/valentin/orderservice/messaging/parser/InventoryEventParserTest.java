package com.valentin.orderservice.messaging.parser;

import com.valentin.orderservice.domain.dictionary.ReservationFailureCode;
import com.valentin.orderservice.messaging.event.InventoryReservationFailedEvent;
import com.valentin.orderservice.messaging.event.InventoryReservedEvent;
import com.valentin.orderservice.messaging.exception.InvalidEventContractException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryEventParserTest {

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RESERVATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private InventoryEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new InventoryEventParser(new JsonMapper());
    }

    @Test
    void parse_whenReservedEventReceived_shouldCreateReservedSubtype() {
        String payload = payload("InventoryReservedEvent", "");

        assertThat(parser.parse(payload))
                .isInstanceOf(InventoryReservedEvent.class)
                .satisfies(event -> {
                    assertThat(event.eventId()).isEqualTo(EVENT_ID);
                    assertThat(event.orderId()).isEqualTo(ORDER_ID);
                    assertThat(event.eventVersion()).isEqualTo(1);
                });
    }

    @Test
    void parse_whenFailureEventReceived_shouldPreserveFailureCode() {
        String payload = payload(
                "InventoryReservationFailedEvent",
                "\"failureCode\": \"INSUFFICIENT_STOCK\","
        );

        assertThat(parser.parse(payload))
                .isInstanceOf(InventoryReservationFailedEvent.class)
                .satisfies(event -> assertThat(
                        ((InventoryReservationFailedEvent) event).failureCode()
                ).isEqualTo(ReservationFailureCode.INSUFFICIENT_STOCK));
    }

    @Test
    void parse_whenEventTypeIsUnknown_shouldRejectPayload() {
        String payload = payload("UnknownInventoryEvent", "");

        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(InvalidEventContractException.class)
                .hasMessage("Cannot parse InventoryEvent");
    }

    private String payload(String eventType, String additionalFields) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "orderId": "%s",
                  "reservationId": "%s",
                  %s
                  "context": {"source": "inventory-service"},
                  "occurredAt": "2026-08-10T10:00:00Z"
                }
                """.formatted(
                EVENT_ID,
                eventType,
                ORDER_ID,
                RESERVATION_ID,
                additionalFields
        );
    }
}
