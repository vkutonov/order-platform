package com.valentin.inventoryservice.messaging.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InventoryReservedEvent(
        @NotNull
        UUID eventId,

        @NotBlank
        String eventType,

        @Positive
        int eventVersion,

        @NotNull
        UUID orderId,

        @NotNull
        UUID reservationId,

        Map<String, String> context,

        @NotNull
        Instant occurredAt
) {

        public static final String TYPE = "InventoryReservedEvent";

        public static final int VERSION = 1;

        public static final Map<String, String> CONTEXT = Map.of("source", "inventory-service");

        public static InventoryReservedEvent of(
                UUID eventId,
                UUID orderId,
                UUID reservationId,
                Instant occurredAt
        ) {
        return new InventoryReservedEvent(
                eventId,
                TYPE,
                VERSION,
                orderId,
                reservationId,
                CONTEXT,
                occurredAt
        );
    }
}
