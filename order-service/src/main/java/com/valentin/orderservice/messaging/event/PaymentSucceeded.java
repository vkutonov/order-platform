package com.valentin.orderservice.messaging.event;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentSucceeded(
        @NotNull UUID eventId,
        @NotBlank String eventType,
        @Positive int eventVersion,
        @NotNull UUID orderId,
        @NotNull UUID paymentId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal totalPrice,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        Map<String, String> context,
        @NotNull Instant occurredAt
) implements PaymentEvent {

    public static final String TYPE = "PaymentSucceeded";
}
