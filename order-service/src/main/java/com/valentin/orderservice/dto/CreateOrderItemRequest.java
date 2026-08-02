package com.valentin.orderservice.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull
        UUID productId,
        @Positive
        @NotNull
        Integer quantity
) {
}
