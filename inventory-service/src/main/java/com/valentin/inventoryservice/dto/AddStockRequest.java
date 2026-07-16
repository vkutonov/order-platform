package com.valentin.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


public record AddStockRequest(
        @NotNull
        @Positive
        int quantityOnHand
) {
}
