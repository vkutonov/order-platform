package com.valentin.paymentservice.domain;

import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.domain.dictionary.RefundStatus;
import com.valentin.paymentservice.exception.InvalidPaymentStatusException;
import com.valentin.paymentservice.exception.InvalidRefundStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RefundEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Test
    void definitiveRefundFailure_isTerminalAndDoesNotChangeCharge() {
        PaymentEntity payment = payment();
        payment.markSucceeded(NOW);
        RefundEntity refund = RefundEntity.create(UUID.randomUUID(), payment, "RESERVATION_EXPIRED", NOW);

        refund.markFailed("REFUND_DECLINED", NOW.plusSeconds(1));
        refund.markFailed("REFUND_DECLINED", NOW.plusSeconds(2));

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureCode()).isEqualTo("REFUND_DECLINED");
        assertThat(refund.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThatThrownBy(() -> refund.markSucceeded("refund-1", NOW))
                .isInstanceOf(InvalidRefundStatusException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "FAILED"})
    void refund_requiresSuccessfulCharge(PaymentStatus status) {
        PaymentEntity payment = payment();
        if (status == PaymentStatus.FAILED) {
            payment.markFailed("DECLINED", NOW);
        }

        assertThatThrownBy(() -> RefundEntity.create(UUID.randomUUID(), payment, "EXPIRED", NOW))
                .isInstanceOf(InvalidPaymentStatusException.class);
    }

    private PaymentEntity payment() {
        return PaymentEntity.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, new BigDecimal("150.00"), "RUB", NOW.plusSeconds(300), NOW);
    }
}
