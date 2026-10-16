package com.valentin.paymentservice.api.dto;

import com.valentin.paymentservice.domain.PaymentEntity;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        BigDecimal totalPrice,
        String currency,
        PaymentStatus status,
        String checkoutUrl,
        Instant expiresAt,
        String failureCode
) {

    public static PaymentResponse from(PaymentEntity payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getTotalPrice(),
                payment.getCurrency(), payment.getStatus(), payment.getCheckoutUrl(),
                payment.getExpiresAt(), payment.getFailureCode());
    }
}
