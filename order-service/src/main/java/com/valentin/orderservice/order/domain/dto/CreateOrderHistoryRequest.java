package com.valentin.orderservice.order.domain.dto;

import com.valentin.orderservice.order.domain.OrderEntity;
import com.valentin.orderservice.order.domain.OrderStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateOrderHistoryRequest(
        @NotNull
        OrderEntity order,
        OrderStatus oldStatus,
        @NotNull
        OrderStatus newStatus,
        String reason
) {
}
