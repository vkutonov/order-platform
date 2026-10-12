package com.valentin.paymentservice.messaging.event;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentRefunded(
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
        UUID refundId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal totalPrice,
        @NotNull @Pattern(regexp = "[A-Z]{3}")
        String currency,
        @NotBlank @Size(max = 200)
        String providerPaymentId,
        @NotBlank @Size(max = 200)
        String providerRefundId,
        Map<String, String> context,
        @NotNull
        Instant occurredAt
) implements PaymentMessage {

    public static final String TYPE = "PaymentRefunded";
    public static final int VERSION = 1;

    public static PaymentRefunded of(
            UUID eventId,
            UUID orderId,
            UUID paymentId,
            UUID refundId,
            BigDecimal totalPrice,
            String currency,
            String providerPaymentId,
            String providerRefundId,
            Instant occurredAt
    ) {
        return new PaymentRefunded(
                eventId, TYPE, VERSION, orderId, paymentId,
                refundId, totalPrice, currency, providerPaymentId, providerRefundId,
                Map.of("source", "payment-service"), occurredAt
        );
    }
}
