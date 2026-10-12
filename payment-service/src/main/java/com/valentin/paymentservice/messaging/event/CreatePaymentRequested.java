package com.valentin.paymentservice.messaging.event;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreatePaymentRequested(
        @NotNull
        UUID eventId,
        @NotBlank
        String eventType,
        @Positive
        int eventVersion,
        @NotNull
        UUID orderId,
        @NotNull
        UUID paymentId,
        @NotNull
        UUID userId,
        @Positive
        int attemptNumber,
        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal totalPrice,
        @NotNull @Pattern(regexp = "[A-Z]{3}")
        String currency,
        @NotNull
        Instant expiresAt,
        Map<String, String> context,
        @NotNull
        Instant occurredAt
) implements PaymentMessage {

    public static final String TYPE = "CreatePaymentRequested";
    public static final int VERSION = 1;

    public static CreatePaymentRequested of(
            UUID eventId,
            UUID orderId,
            UUID paymentId,
            UUID userId,
            int attemptNumber,
            BigDecimal totalPrice,
            String currency,
            Instant expiresAt,
            Instant occurredAt
    ) {
        return new CreatePaymentRequested(
                eventId, TYPE, VERSION, orderId, paymentId,
                userId, attemptNumber, totalPrice, currency, expiresAt,
                Map.of("source", "order-service"), occurredAt
        );
    }
}
