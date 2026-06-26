package com.valentin.orderservice.dto;

import com.valentin.orderservice.domain.OrderStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record OrderHistoryResponse(
        @NotNull
        UUID id,
        @NotNull
        UUID orderId,
        OrderStatus oldStatus,
        @NotNull
        OrderStatus newStatus,
        String reason,
        Instant createdAt
) {
}
