package com.valentin.inventoryservice.dto;

import com.valentin.inventoryservice.domain.dictionary.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryProductResponse(
        UUID productId,
        String name,
        String description,
        BigDecimal unitPrice,
        String currency,
        ProductStatus status,
        Integer quantityOnHand,
        Integer reservedQuantity,
        Integer availableQuantity
) {
}
