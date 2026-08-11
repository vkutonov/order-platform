package com.valentin.orderservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "eventType",
        visible = true
)

@JsonSubTypes({
        @JsonSubTypes.Type(
                value = InventoryReservedEvent.class,
                name = "InventoryReservedEvent"
        ),
        @JsonSubTypes.Type(
                value = InventoryReservationFailedEvent.class,
                name = "InventoryReservationFailedEvent"
        ),
})

public sealed interface InventoryEvent
    permits InventoryReservedEvent,
        InventoryReservationFailedEvent {

        UUID eventId();

        String eventType();

        int eventVersion();

        UUID orderId();

}
