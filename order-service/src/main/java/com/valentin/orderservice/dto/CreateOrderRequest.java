package com.valentin.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest (
        @NotNull
        UUID userId,
        @NotEmpty
        @Valid
        List<CreateOrderItemRequest> items
) {
}
