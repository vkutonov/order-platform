package com.valentin.paymentservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaymentSucceeded.class, name = PaymentSucceeded.TYPE),
        @JsonSubTypes.Type(value = PaymentFailed.class, name = PaymentFailed.TYPE),
        @JsonSubTypes.Type(value = PaymentRefunded.class, name = PaymentRefunded.TYPE),
        @JsonSubTypes.Type(value = PaymentRefundFailed.class, name = PaymentRefundFailed.TYPE)
})
public sealed interface PaymentMessage permits PaymentSucceeded, PaymentFailed, PaymentRefunded, PaymentRefundFailed {

    UUID eventId();

    String eventType();

    int eventVersion();

    UUID orderId();

    UUID paymentId();

    Instant occurredAt();
}
