package com.valentin.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PreparedOrderItem(
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        int quantity
) {
}
