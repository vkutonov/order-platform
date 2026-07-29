package com.valentin.inventoryservice.dto;

import jakarta.validation.constraints.Positive;


public record AddStockRequest(
        @Positive
        int quantity
) {
}
