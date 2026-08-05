package com.valentin.inventoryservice.messaging.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderCreatedEvent(
        @NotNull
        UUID eventId,

        @NotBlank
        String eventType,

        @Positive
        int eventVersion,

        @NotNull
        UUID orderId,

        @NotNull
        UUID userId,

        @NotEmpty
        List<@Valid OrderCreatedItem> items,


        Map<String, String> context,

        @NotNull
        Instant occurredAt
) {
}
