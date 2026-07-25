package com.valentin.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReservationItemCommand(
        @NotNull
        UUID productId,
        @Positive
        int quantity
) {
}
