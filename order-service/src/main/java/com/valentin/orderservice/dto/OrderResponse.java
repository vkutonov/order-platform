package com.valentin.orderservice.dto;

import com.valentin.orderservice.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        OrderStatus status,
        BigDecimal totalPrice,
        String currency,
        List<OrderItemResponse> orderItems,
        Instant createdAt,
        Instant updatedAt
) {
}
