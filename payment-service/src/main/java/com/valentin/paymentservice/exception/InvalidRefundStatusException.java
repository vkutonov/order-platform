package com.valentin.paymentservice.exception;

import com.valentin.paymentservice.domain.dictionary.RefundStatus;

import java.util.UUID;

public class InvalidRefundStatusException extends RuntimeException {

    public InvalidRefundStatusException(UUID refundId, RefundStatus currentStatus, RefundStatus targetStatus) {
        super("Invalid refund status transition: refundId=%s, %s -> %s"
                .formatted(refundId, currentStatus, targetStatus));
    }
}
