package com.valentin.orderservice.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OrderReadyForPaymentEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID orderId,
        UUID userId,
        BigDecimal totalPrice,
        String currency,
        Map<String, String> context,
        Instant occurredAt
) {
    public static final String TYPE = "OrderReadyForPaymentEvent";
    public static final int VERSION = 1;

    public static OrderReadyForPaymentEvent of(
            UUID eventId,
            UUID orderId,
            UUID userId,
            BigDecimal totalPrice,
            String currency,
            Instant occurredAt
    ) {
        return new OrderReadyForPaymentEvent(
                eventId,
                TYPE,
                VERSION,
                orderId,
                userId,
                totalPrice,
                currency,
                Map.of("source", "order-service"),
                occurredAt
        );
    }
}
