package com.valentin.paymentservice.logic;

import com.valentin.paymentservice.api.dto.PaymentResponse;
import com.valentin.paymentservice.db.PaymentRepository;
import com.valentin.paymentservice.db.ProcessedEventRepository;
import com.valentin.paymentservice.db.RefundRepository;
import com.valentin.paymentservice.domain.PaymentEntity;
import com.valentin.paymentservice.domain.RefundEntity;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.messaging.event.OrderReadyForPaymentEvent;
import com.valentin.paymentservice.messaging.event.PaymentFailed;
import com.valentin.paymentservice.messaging.event.PaymentSucceeded;
import com.valentin.paymentservice.messaging.event.PaymentRefunded;
import com.valentin.paymentservice.messaging.event.OrderLifecycleEvent;
import com.valentin.paymentservice.messaging.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository payments;
    private final OutboxEventService outbox;
    private final Clock clock;
    private final ProcessedEventRepository inbox;
    private final RefundRepository refunds;

    @Value("${app.payment.expiration-minutes}")
    private long expirationMinutes;

    @Transactional
    public PaymentResponse createFromOrder(OrderReadyForPaymentEvent event) {
        var existing = payments.findByOrderIdAndAttemptNumber(event.orderId(), 1);

        if (existing.isPresent()) {
            PaymentEntity payment = existing.get();

            if (!payment.getUserId().equals(event.userId())
                    || payment.getTotalPrice().compareTo(event.totalPrice()) != 0
                    || !payment.getCurrency().equals(event.currency())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order payment already exists with other details");
            }

            log.debug("Order payment already exists: eventId={}, orderId={}, paymentId={}, status={}",
                    event.eventId(), event.orderId(), payment.getId(), payment.getStatus());

            return PaymentResponse.from(payment);
        }

        var now = clock.instant();

        var expiresAt = event.occurredAt().plus(expirationMinutes, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.MICROS);

        PaymentEntity payment = PaymentEntity.create(UUID.randomUUID(), event.orderId(), event.userId(),
                1, event.totalPrice(), event.currency(), expiresAt, now);

        boolean cancelled = inbox.existsByAggregateIdAndEventType(event.orderId(), OrderLifecycleEvent.CANCELLED);

        if (cancelled || !expiresAt.isAfter(now)) {
            payment.markFailed(cancelled ? "ORDER_CANCELLED" : "SESSION_EXPIRED", now);
            outbox.enqueue(PaymentFailed.of(UUID.randomUUID(), payment.getOrderId(), payment.getId(),
                    payment.getTotalPrice(), payment.getCurrency(), payment.getFailureCode(), now));
        }

        payments.saveAndFlush(payment);
        var response = PaymentResponse.from(payment);

        afterCommit(() -> log.info("Payment created: eventId={}, paymentId={}, orderId={}, status={}, failureCode={}",
                event.eventId(), response.paymentId(), response.orderId(), response.status(), response.failureCode()));

        return response;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAll() {
        return payments.findAll(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))
                .stream().map(PaymentResponse::from).toList();
    }

    @Transactional
    public PaymentResponse confirm(UUID orderId) {
        return complete(orderId, true);
    }

    @Transactional
    public PaymentResponse reject(UUID orderId) {
        return complete(orderId, false);
    }

    @Transactional
    public void cancelFromOrder(UUID orderId) {
        var existing = payments.findByOrderIdAndAttemptNumberForUpdate(orderId, 1);

        if (existing.isEmpty()) {
            return;
        }

        var payment = existing.get();

        var now = clock.instant();

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markFailed("ORDER_CANCELLED", now);

            outbox.enqueue(PaymentFailed.of(UUID.randomUUID(), orderId, payment.getId(),
                    payment.getTotalPrice(), payment.getCurrency(), payment.getFailureCode(), now));

            afterCommit(() -> log.info("Payment cancelled: paymentId={}, orderId={}", payment.getId(), orderId));
        } else if (payment.getStatus() == PaymentStatus.SUCCEEDED && refunds.findByPaymentId(payment.getId()).isEmpty()) {
            var refund = RefundEntity.create(UUID.randomUUID(), payment, "ORDER_CANCELLED", now);

            refund.markSucceeded("mock-" + refund.getId(), now);
            refunds.save(refund);

            outbox.enqueue(PaymentRefunded.of(UUID.randomUUID(), orderId, payment.getId(), refund.getId(),
                    payment.getTotalPrice(), payment.getCurrency(), "mock-" + payment.getId(),
                    refund.getProviderRefundId(), now));

            afterCommit(() -> log.info("Mock payment refunded for cancelled order: paymentId={}, orderId={}, refundId={}",
                    payment.getId(), orderId, refund.getId()));
        }
    }

    private PaymentResponse complete(UUID orderId, boolean confirmed) {
        PaymentEntity payment = payments.findByOrderIdAndAttemptNumberForUpdate(orderId, 1)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        return complete(payment, confirmed);
    }

    @Transactional
    public void expire(UUID orderId) {
        payments.findByOrderIdAndAttemptNumberForUpdate(orderId, 1)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .filter(payment -> !payment.getExpiresAt().isAfter(clock.instant()))
                .ifPresent(payment -> complete(payment, false));
    }

    private PaymentResponse complete(PaymentEntity payment, boolean confirmed) {
        PaymentStatus oldStatus = payment.getStatus();
        if (oldStatus == PaymentStatus.SUCCEEDED || oldStatus == PaymentStatus.FAILED) {
            log.debug("Payment completion skipped: paymentId={}, orderId={}, status={}",
                    payment.getId(), payment.getOrderId(), payment.getStatus());
            return PaymentResponse.from(payment);
        }

        var now = clock.instant();
        UUID eventId = UUID.randomUUID();
        if (confirmed && payment.getExpiresAt().isAfter(now)) {
            payment.markSucceeded(now);
            outbox.enqueue(PaymentSucceeded.of(eventId, payment.getOrderId(), payment.getId(),
                    payment.getTotalPrice(), payment.getCurrency(), now));
        } else {
            payment.markFailed(payment.getExpiresAt().isAfter(now) ? "DECLINED" : "SESSION_EXPIRED", now);
            outbox.enqueue(PaymentFailed.of(eventId, payment.getOrderId(), payment.getId(),
                    payment.getTotalPrice(), payment.getCurrency(), payment.getFailureCode(), now));
        }
        var response = PaymentResponse.from(payment);
        afterCommit(() -> log.info(
                "Payment status changed: paymentId={}, orderId={}, oldStatus={}, newStatus={}, failureCode={}, eventId={}",
                response.paymentId(), response.orderId(), oldStatus, response.status(),
                response.failureCode(), eventId));
        return response;
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
