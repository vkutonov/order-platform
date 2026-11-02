package com.valentin.paymentservice.logic;

import com.valentin.paymentservice.db.ProcessedEventRepository;
import com.valentin.paymentservice.messaging.event.OrderReadyForPaymentEvent;
import com.valentin.paymentservice.messaging.event.OrderLifecycleEvent;
import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderReadyForPaymentEventHandler {

    private final ProcessedEventRepository inbox;
    private final PaymentService payments;
    private final Clock clock;

    @Transactional
    public void handle(OrderReadyForPaymentEvent event) {
        inbox.lockOrder(event.orderId());
        if (inbox.tryInsert(event.eventId(), event.eventType(), event.orderId(), clock.instant()) == 0) {
            log.debug("Order readiness already processed: eventId={}, orderId={}", event.eventId(), event.orderId());
            return;
        }
        try {
            payments.createFromOrder(event);
        } catch (ResponseStatusException exception) {
            throw new InvalidEventContractException("Order readiness conflicts with stored payment details", exception);
        }
    }

    @Transactional
    public void handleCancellation(OrderLifecycleEvent event) {
        inbox.lockOrder(event.orderId());
        if (inbox.tryInsert(event.eventId(), event.eventType(), event.orderId(), clock.instant()) == 0) {
            return;
        }
        payments.cancelFromOrder(event.orderId());
    }
}
