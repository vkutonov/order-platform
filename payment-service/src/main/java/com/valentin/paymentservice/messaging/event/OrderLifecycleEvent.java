package com.valentin.paymentservice.messaging.event;

import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import com.valentin.paymentservice.messaging.exception.UnsupportedEventVersionException;

import java.time.Instant;
import java.util.UUID;

public record OrderLifecycleEvent(
        UUID eventId, String eventType, int eventVersion, UUID orderId, Instant occurredAt
) {
    public static final String PAID = "OrderPaidEvent";
    public static final String CANCELLED = "OrderCancelledEvent";

    public void validate(String key) {
        if (eventId == null || orderId == null || occurredAt == null
                || !orderId.toString().equals(key)
                || !(PAID.equals(eventType) || CANCELLED.equals(eventType))) {
            throw new InvalidEventContractException("Invalid order lifecycle event or Kafka key");
        }
        if (eventVersion != 1) {
            throw new UnsupportedEventVersionException(eventVersion, 1);
        }
    }
}
