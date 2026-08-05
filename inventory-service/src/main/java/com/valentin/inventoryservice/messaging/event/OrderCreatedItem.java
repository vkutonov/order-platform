package com.valentin.inventoryservice.messaging.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedItem (
        @NotNull
        UUID productId,

        String productName,
        BigDecimal unitPrice,
        String currency,

        @NotNull
        @Positive
        Integer quantity
) {
}
