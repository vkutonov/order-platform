package com.valentin.paymentservice.domain;

import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.domain.dictionary.RefundStatus;
import com.valentin.paymentservice.exception.InvalidPaymentStatusException;
import com.valentin.paymentservice.exception.InvalidRefundStatusException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "refunds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    @Column(nullable = false, updatable = false, length = 100)
    private String reason;

    @Column(name = "provider_refund_id", length = 200)
    private String providerRefundId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static RefundEntity create(UUID refundId, PaymentEntity payment, String reason, Instant now) {
        Objects.requireNonNull(payment);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new InvalidPaymentStatusException(payment.getId(), payment.getStatus(), PaymentStatus.SUCCEEDED);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Refund reason must not be blank");
        }

        RefundEntity refund = new RefundEntity();
        refund.id = Objects.requireNonNull(refundId);
        refund.paymentId = payment.getId();
        refund.status = RefundStatus.PENDING;
        refund.reason = reason;
        refund.createdAt = Objects.requireNonNull(now);
        refund.updatedAt = now;
        return refund;
    }

    public void markSucceeded(String providerRefundId, Instant now) {
        if (providerRefundId == null || providerRefundId.isBlank()) {
            throw new IllegalArgumentException("Provider refund ID must not be blank");
        }
        if (status == RefundStatus.SUCCEEDED && Objects.equals(this.providerRefundId, providerRefundId)) {
            return;
        }
        changeStatus(RefundStatus.SUCCEEDED, now);
        this.providerRefundId = providerRefundId;
    }

    public void markFailed(String failureCode, Instant now) {
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("Failure code must not be blank");
        }
        if (status == RefundStatus.FAILED && Objects.equals(this.failureCode, failureCode)) {
            return;
        }
        changeStatus(RefundStatus.FAILED, now);
        this.failureCode = failureCode;
    }

    private void changeStatus(RefundStatus targetStatus, Instant now) {
        if (status != RefundStatus.PENDING) {
            throw new InvalidRefundStatusException(id, status, targetStatus);
        }
        updatedAt = Objects.requireNonNull(now);
        status = targetStatus;
    }
}
