package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.PaymentResultRepository;
import com.valentin.orderservice.db.ProcessedEventRepository;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.PaymentResultEntity;
import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.domain.dictionary.PaymentResultStatus;
import com.valentin.orderservice.messaging.event.PaymentEvent;
import com.valentin.orderservice.messaging.event.PaymentFailed;
import com.valentin.orderservice.messaging.event.PaymentSucceeded;
import com.valentin.orderservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventHandler {

    private final ProcessedEventRepository inbox;
    private final OrderRepository orders;
    private final PaymentResultRepository results;
    private final OrderHistoryRepository history;
    private final Clock clock;

    @Transactional
    public void handle(PaymentEvent event) {
        var now = clock.instant();

        if (inbox.tryInsert(event.eventId(), event.eventType(), event.orderId(), now) == 0) {
            log.debug("Payment event already processed: eventId={}, paymentId={}, orderId={}",
                    event.eventId(), event.paymentId(), event.orderId());
            return;
        }

        var order = orders.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new InvalidEventContractException("Payment result references an unknown order"));

        if (order.getTotalPrice().compareTo(event.totalPrice()) != 0
                || !order.getCurrency().equals(event.currency())) {
            throw new InvalidEventContractException("Payment amount or currency does not match the order");
        }

        PaymentResultStatus resultStatus = event instanceof PaymentSucceeded
                ? PaymentResultStatus.SUCCEEDED : PaymentResultStatus.FAILED;

        String failureCode = event instanceof PaymentFailed failed ? failed.failureCode() : null;

        var existing = results.findById(event.paymentId());
        if (existing.isPresent()) {
            var result = existing.get();
            if (!result.getOrderId().equals(event.orderId()) || result.getStatus() != resultStatus
                    || !Objects.equals(result.getFailureCode(), failureCode)) {
                throw new InvalidEventContractException("Payment result conflicts with the stored final result");
            }
            log.debug("Payment result already applied: eventId={}, paymentId={}, orderId={}",
                    event.eventId(), event.paymentId(), event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.WAITING_FOR_INVENTORY) {
            throw new IllegalStateException("Order is not ready for a payment result yet");
        }

        results.save(PaymentResultEntity.create(event.paymentId(), event.orderId(), resultStatus,
                failureCode, event.occurredAt()));

        OrderStatus oldStatus = order.getStatus();

        if (oldStatus != OrderStatus.WAITING_FOR_PAYMENT) {
            afterCommit(() -> log.warn(
                    "Payment result saved without changing order status: eventId={}, paymentId={}, orderId={}, result={}, orderStatus={}",
                    event.eventId(), event.paymentId(), event.orderId(), resultStatus, oldStatus));
            return;
        }

        if (resultStatus == PaymentResultStatus.SUCCEEDED) {
            order.markPaymentSucceeded(now);
        }

        OrderChangeHistoryReason reason = resultStatus == PaymentResultStatus.SUCCEEDED
                ? OrderChangeHistoryReason.PAYMENT_SUCCEEDED : OrderChangeHistoryReason.PAYMENT_FAILED;

        history.save(OrderHistoryEntity.create(order, oldStatus, order.getStatus(), reason, now));

        OrderStatus newStatus = order.getStatus();

        afterCommit(() -> log.info(
                "Order payment result applied: eventId={}, paymentId={}, orderId={}, result={}, failureCode={}, oldStatus={}, newStatus={}",
                event.eventId(), event.paymentId(), event.orderId(), resultStatus, failureCode, oldStatus, newStatus));
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
