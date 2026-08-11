package com.valentin.orderservice.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID orderId,
        UUID userId,
        List<OrderCreatedItem> items,
        Map<String, String> context,
        Instant occurredAt
) {
    public static final String TYPE =
            "OrderCreatedEvent";

    public static final int VERSION = 1;

    public static OrderCreatedEvent of(
            UUID eventId,
            UUID orderId,
            UUID userId,
            List<OrderCreatedItem> items,
            Map<String, String> context,
            Instant occurredAt
    ) {
        return new OrderCreatedEvent(
                eventId,
                TYPE,
                VERSION,
                orderId,
                userId,
                List.copyOf(items),
                Map.copyOf(context),
                occurredAt
        );
    }
}
