package com.valentin.inventoryservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;


public record CreateProductRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        String description,

        @NotNull
        @Positive
        BigDecimal unitPrice,

        @NotNull
        @Size(min = 3, max = 3)
        @Pattern(regexp = "[A-Z]{3}")
        String currency,

        @PositiveOrZero
        int quantityOnHand
) {
}