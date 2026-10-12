package com.valentin.paymentservice.exception;

import com.valentin.paymentservice.domain.dictionary.PaymentStatus;

import java.util.UUID;

public class InvalidPaymentStatusException extends RuntimeException {

    public InvalidPaymentStatusException(UUID paymentId, PaymentStatus current, PaymentStatus target) {
        super("Invalid payment status transition: paymentId=%s, current=%s, target=%s"
                .formatted(paymentId, current, target));
    }
}
