package com.valentin.orderservice.dto;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull
        UUID productId,
        @NotBlank
        String productName,
        @NotNull
        @PositiveOrZero
        BigDecimal unitPrice,
        @Positive
        @NotNull
        Integer quantity
) {
}
