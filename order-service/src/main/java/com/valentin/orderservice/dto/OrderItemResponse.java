package com.valentin.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse (
        UUID id,
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal totalPrice
) {
}
