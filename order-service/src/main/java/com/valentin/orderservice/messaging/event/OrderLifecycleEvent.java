package com.valentin.orderservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record OrderLifecycleEvent(
        UUID eventId, String eventType, int eventVersion, UUID orderId, Instant occurredAt
) {
    public static final String PAID = "OrderPaidEvent";
    public static final String CANCELLED = "OrderCancelledEvent";

    public static OrderLifecycleEvent of(String type, UUID orderId, Instant now) {
        return new OrderLifecycleEvent(UUID.randomUUID(), type, 1, orderId, now);
    }
}
