package com.valentin.inventoryservice.dto;

import com.valentin.inventoryservice.domain.dictionary.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshotResponse(
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        String currency,
        ProductStatus status
) {
}
