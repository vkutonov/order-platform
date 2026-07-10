package com.valentin.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderSummaryResponseList(
        BigDecimal totalPrice,
        List<OrderSummaryResponse> orderSummaryResponses
) {
}
