package com.valentin.inventoryservice.messaging.event;

import com.valentin.inventoryservice.domain.dictionary.ReservationFailureCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InventoryReservationFailedEvent(
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

        @NotNull
        ReservationFailureCode failureCode,

        Map<String, String> context,

        @NotNull
        Instant occurredAt
) {
        public static final String TYPE = "InventoryReservationFailedEvent";

        public static final int VERSION = 1;

        public static final Map<String, String> CONTEXT = Map.of("source", "inventory-service");

        public static InventoryReservationFailedEvent of(
                UUID eventId,
                UUID orderId,
                UUID reservationId,
                ReservationFailureCode failureCode,
                Instant occurredAt
        ) {
        return new InventoryReservationFailedEvent(
                eventId,
                TYPE,
                VERSION,
                orderId,
                reservationId,
                failureCode,
                CONTEXT,
                occurredAt
        );
    }
}
