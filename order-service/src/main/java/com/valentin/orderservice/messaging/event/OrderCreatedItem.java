package com.valentin.orderservice.messaging.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedItem(
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
