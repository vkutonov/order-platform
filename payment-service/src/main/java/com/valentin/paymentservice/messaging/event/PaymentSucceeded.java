package com.valentin.paymentservice.messaging.event;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentSucceeded(
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
        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal totalPrice,
        @NotNull @Pattern(regexp = "[A-Z]{3}")
        String currency,
        @NotBlank @Size(max = 200)
        String providerPaymentId,
        Map<String, String> context,
        @NotNull
        Instant occurredAt
) implements PaymentMessage {

    public static final String TYPE = "PaymentSucceeded";
    public static final int VERSION = 1;

    public static PaymentSucceeded of(
            UUID eventId,
            UUID orderId,
            UUID paymentId,
            BigDecimal totalPrice,
            String currency,
            String providerPaymentId,
            Instant occurredAt
    ) {
        return new PaymentSucceeded(
                eventId, TYPE, VERSION, orderId, paymentId,
                totalPrice, currency, providerPaymentId,
                Map.of("source", "payment-service"), occurredAt
        );
    }
}
