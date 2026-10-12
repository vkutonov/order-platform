package com.valentin.paymentservice.messaging.event;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentSessionCreated(
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
        @NotBlank @Size(max = 2048)
        String checkoutUrl,
        @NotNull
        Instant expiresAt,
        Map<String, String> context,
        @NotNull
        Instant occurredAt
) implements PaymentMessage {

    public static final String TYPE = "PaymentSessionCreated";
    public static final int VERSION = 1;

    public static PaymentSessionCreated of(
            UUID eventId,
            UUID orderId,
            UUID paymentId,
            BigDecimal totalPrice,
            String currency,
            String providerPaymentId,
            String checkoutUrl,
            Instant expiresAt,
            Instant occurredAt
    ) {
        return new PaymentSessionCreated(
                eventId, TYPE, VERSION, orderId, paymentId,
                totalPrice, currency, providerPaymentId, checkoutUrl, expiresAt,
                Map.of("source", "payment-service"), occurredAt
        );
    }
}
