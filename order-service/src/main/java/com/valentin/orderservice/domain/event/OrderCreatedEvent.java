package com.valentin.orderservice.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID userId,
        List<OrderCreatedItemPayload> items,
        Map<String, String> context,
        Instant occurredAt
) {
    public static OrderCreatedEvent of(
            UUID orderId,
            UUID userId,
            List<OrderCreatedItemPayload> items,
            Map<String, String> context,
            Instant occurredAt
    ) {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                orderId,
                userId,
                items,
                context,
                occurredAt
        );
    }
}
