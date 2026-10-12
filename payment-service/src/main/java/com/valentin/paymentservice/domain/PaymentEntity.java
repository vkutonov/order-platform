package com.valentin.paymentservice.domain;

import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.exception.InvalidPaymentStatusException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.CREATING, Set.of(PaymentStatus.PENDING, PaymentStatus.FAILED),
            PaymentStatus.PENDING, Set.of(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED),
            PaymentStatus.SUCCEEDED, Set.of(),
            PaymentStatus.FAILED, Set.of()
    );

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "total_price", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "provider_payment_id", length = 200)
    private String providerPaymentId;

    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static PaymentEntity create(
            UUID paymentId,
            UUID orderId,
            UUID userId,
            int attemptNumber,
            BigDecimal totalPrice,
            String currency,
            Instant expiresAt,
            Instant now
    ) {
        Objects.requireNonNull(totalPrice, "totalPrice must not be null");
        if (attemptNumber <= 0 || totalPrice.signum() <= 0 || totalPrice.scale() > 2
                || totalPrice.setScale(2).precision() > 19) {
            throw new IllegalArgumentException("Attempt number and monetary amount must be valid and positive");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must contain three uppercase letters");
        }

        PaymentEntity payment = new PaymentEntity();
        payment.id = Objects.requireNonNull(paymentId);
        payment.orderId = Objects.requireNonNull(orderId);
        payment.userId = Objects.requireNonNull(userId);
        payment.attemptNumber = attemptNumber;
        payment.totalPrice = totalPrice.setScale(2);
        payment.currency = currency;
        payment.status = PaymentStatus.CREATING;
        payment.expiresAt = Objects.requireNonNull(expiresAt);
        payment.createdAt = Objects.requireNonNull(now);
        payment.updatedAt = now;
        return payment;
    }

    public void markSessionCreated(String providerPaymentId, String checkoutUrl, Instant now) {
        if (providerPaymentId == null || providerPaymentId.isBlank()
                || checkoutUrl == null || checkoutUrl.isBlank()) {
            throw new IllegalArgumentException("Provider payment ID and checkout URL must not be blank");
        }
        if (status == PaymentStatus.PENDING && Objects.equals(this.providerPaymentId, providerPaymentId)
                && Objects.equals(this.checkoutUrl, checkoutUrl)) {
            return;
        }
        changeStatus(PaymentStatus.PENDING, now);
        this.providerPaymentId = providerPaymentId;
        this.checkoutUrl = checkoutUrl;
    }

    public void markSucceeded(Instant now) {
        if (status == PaymentStatus.SUCCEEDED) {
            return;
        }
        changeStatus(PaymentStatus.SUCCEEDED, now);
    }

    public void markFailed(String failureCode, Instant now) {
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("Failure code must not be blank");
        }
        if (status == PaymentStatus.FAILED && Objects.equals(this.failureCode, failureCode)) {
            return;
        }
        changeStatus(PaymentStatus.FAILED, now);
        this.failureCode = failureCode;
    }

    private void changeStatus(PaymentStatus targetStatus, Instant now) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(targetStatus)) {
            throw new InvalidPaymentStatusException(id, status, targetStatus);
        }
        updatedAt = Objects.requireNonNull(now);
        status = targetStatus;
    }
}
