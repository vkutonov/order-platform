package com.valentin.orderservice.dto;

import com.valentin.orderservice.domain.dictionary.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        String currency,
        ProductStatus status
) {
}
