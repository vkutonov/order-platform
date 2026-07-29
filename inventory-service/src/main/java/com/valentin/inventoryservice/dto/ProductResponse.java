package com.valentin.inventoryservice.dto;

import com.valentin.inventoryservice.domain.dictionary.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal unitPrice,
        String currency,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}