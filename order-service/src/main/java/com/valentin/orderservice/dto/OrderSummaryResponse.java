package com.valentin.orderservice.dto;

import com.valentin.orderservice.domain.dictionary.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        UUID userId,
        OrderStatus status,
        BigDecimal totalPrice,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
