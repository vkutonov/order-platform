package com.valentin.orderservice.order.domain.dto;

import jakarta.persistence.criteria.CriteriaBuilder;
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
        Integer quantity
) {
}
