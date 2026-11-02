package com.valentin.orderservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaymentSucceeded.class, name = PaymentSucceeded.TYPE),
        @JsonSubTypes.Type(value = PaymentFailed.class, name = PaymentFailed.TYPE)
})
public sealed interface PaymentEvent permits PaymentSucceeded, PaymentFailed {

    UUID eventId();

    String eventType();

    int eventVersion();

    UUID orderId();

    UUID paymentId();

    BigDecimal totalPrice();

    String currency();

    Instant occurredAt();
}
