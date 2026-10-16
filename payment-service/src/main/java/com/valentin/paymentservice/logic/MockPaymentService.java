package com.valentin.paymentservice.logic;

import com.valentin.paymentservice.api.dto.MockPaymentRequest;
import com.valentin.paymentservice.api.dto.PaymentResponse;
import com.valentin.paymentservice.db.PaymentRepository;
import com.valentin.paymentservice.domain.PaymentEntity;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.messaging.event.PaymentFailed;
import com.valentin.paymentservice.messaging.event.PaymentSessionCreated;
import com.valentin.paymentservice.messaging.event.PaymentSucceeded;
import com.valentin.paymentservice.messaging.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MockPaymentService {

    private final PaymentRepository payments;
    private final OutboxEventService outbox;
    private final Clock clock;

    @Value("${app.payment.mock.public-url}")
    private String publicUrl;

    @Transactional
    public PaymentResponse create(MockPaymentRequest request) {
        var expiresAt = request.expiresAt().truncatedTo(ChronoUnit.MICROS);
        var existing = payments.findByIdForUpdate(request.paymentId());
        if (existing.isPresent()) {
            PaymentEntity payment = existing.get();
            if (!payment.getOrderId().equals(request.orderId())
                    || !payment.getUserId().equals(request.userId())
                    || payment.getAttemptNumber() != request.attemptNumber()
                    || payment.getTotalPrice().compareTo(request.totalPrice()) != 0
                    || !payment.getCurrency().equals(request.currency())
                    || !payment.getExpiresAt().equals(expiresAt)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment ID is already used with other details");
            }
            return PaymentResponse.from(payment);
        }

        var now = clock.instant();
        if (!expiresAt.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment has already expired");
        }

        PaymentEntity payment = PaymentEntity.create(request.paymentId(), request.orderId(), request.userId(),
                request.attemptNumber(), request.totalPrice(), request.currency(), expiresAt, now);

        String checkoutUrl = publicUrl.replaceAll("/+$", "") + "/mock-payments/" + payment.getId();
        payment.markSessionCreated("mock-" + payment.getId(), checkoutUrl, now);

        payments.saveAndFlush(payment);
        outbox.enqueue(PaymentSessionCreated.of(UUID.randomUUID(), payment.getOrderId(), payment.getId(),
                payment.getTotalPrice(), payment.getCurrency(), payment.getProviderPaymentId(),
                checkoutUrl, expiresAt, now));

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId) {
        return PaymentResponse.from(payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found")));
    }

    @Transactional
    public void complete(UUID paymentId, boolean paid) {
        PaymentEntity payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment session is not ready");
        }
        var now = clock.instant();
        boolean expired = !payment.getExpiresAt().isAfter(now);
        if (paid && !expired) {
            payment.markSucceeded(now);
            outbox.enqueue(PaymentSucceeded.of(UUID.randomUUID(), payment.getOrderId(), payment.getId(),
                    payment.getTotalPrice(), payment.getCurrency(), payment.getProviderPaymentId(), now));
        } else {
            payment.markFailed(expired ? "SESSION_EXPIRED" : "DECLINED", now);
            outbox.enqueue(PaymentFailed.of(UUID.randomUUID(), payment.getOrderId(), payment.getId(),
                    payment.getTotalPrice(), payment.getCurrency(), payment.getProviderPaymentId(),
                    payment.getFailureCode(), now));
        }
    }
}
