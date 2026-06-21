package com.valentin.orderservice.order.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest (
        @NotNull
        UUID userId,
        @NotEmpty
        List<CreateOrderItemRequest> items
) {
}
