package com.valentin.orderservice.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedItemPayload(
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        String currency,
        int quantity
) {
    public BigDecimal totalPrice() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
