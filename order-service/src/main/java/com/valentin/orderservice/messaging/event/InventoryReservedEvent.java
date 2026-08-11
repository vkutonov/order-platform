package com.valentin.orderservice.messaging.event;

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
) implements InventoryEvent {
}
