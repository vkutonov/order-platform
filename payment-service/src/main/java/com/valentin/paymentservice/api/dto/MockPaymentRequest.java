package com.valentin.paymentservice.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MockPaymentRequest(
        @NotNull UUID paymentId,
        @NotNull UUID orderId,
        @NotNull UUID userId,
        @Positive int attemptNumber,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal totalPrice,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull Instant expiresAt
) {
}
