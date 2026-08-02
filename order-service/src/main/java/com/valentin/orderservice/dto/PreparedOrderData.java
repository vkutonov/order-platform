package com.valentin.orderservice.dto;

import java.util.List;
import java.util.UUID;

public record PreparedOrderData(
        UUID userId,
        String currency,
        List<PreparedOrderItem> items
) {
}
