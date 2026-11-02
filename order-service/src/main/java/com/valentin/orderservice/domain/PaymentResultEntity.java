package com.valentin.orderservice.domain;

import com.valentin.orderservice.domain.dictionary.PaymentResultStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_payment_results")
public class PaymentResultEntity {

    @Id
    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PaymentResultStatus status;

    @Column(name = "failure_code", updatable = false, length = 100)
    private String failureCode;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static PaymentResultEntity create(
            UUID paymentId,
            UUID orderId,
            PaymentResultStatus status,
            String failureCode,
            Instant occurredAt
    ) {
        var result = new PaymentResultEntity();
        result.paymentId = paymentId;
        result.orderId = orderId;
        result.status = status;
        result.failureCode = failureCode;
        result.occurredAt = occurredAt;
        return result;
    }
}
