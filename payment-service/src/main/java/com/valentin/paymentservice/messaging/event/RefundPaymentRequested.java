package com.valentin.paymentservice.messaging.event;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RefundPaymentRequested(
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
        @NotBlank @Size(max = 100)
        String reason,
        Map<String, String> context,
        @NotNull
        Instant occurredAt
) implements PaymentMessage {

    public static final String TYPE = "RefundPaymentRequested";
    public static final int VERSION = 1;

    public static RefundPaymentRequested of(
            UUID eventId,
            UUID orderId,
            UUID paymentId,
            UUID refundId,
            BigDecimal totalPrice,
            String currency,
            String reason,
            Instant occurredAt
    ) {
        return new RefundPaymentRequested(
                eventId, TYPE, VERSION, orderId, paymentId,
                refundId, totalPrice, currency, reason,
                Map.of("source", "order-service"), occurredAt
        );
    }
}
