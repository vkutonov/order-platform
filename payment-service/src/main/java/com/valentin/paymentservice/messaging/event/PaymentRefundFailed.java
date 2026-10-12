package com.valentin.paymentservice.messaging.event;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentRefundFailed(
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
        @NotBlank @Size(max = 100)
        String failureCode,
        Map<String, String> context,
        @NotNull
        Instant occurredAt
) implements PaymentMessage {

    public static final String TYPE = "PaymentRefundFailed";
    public static final int VERSION = 1;

    public static PaymentRefundFailed of(
            UUID eventId,
            UUID orderId,
            UUID paymentId,
            UUID refundId,
            BigDecimal totalPrice,
            String currency,
            String providerPaymentId,
            String failureCode,
            Instant occurredAt
    ) {
        return new PaymentRefundFailed(
                eventId, TYPE, VERSION, orderId, paymentId,
                refundId, totalPrice, currency, providerPaymentId, failureCode,
                Map.of("source", "payment-service"), occurredAt
        );
    }
}
