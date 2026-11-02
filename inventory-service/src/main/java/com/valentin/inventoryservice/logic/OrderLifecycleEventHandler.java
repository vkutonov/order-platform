package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.ProcessedEventsRepository;
import com.valentin.inventoryservice.db.ReservationRepository;
import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;
import com.valentin.inventoryservice.messaging.event.OrderLifecycleEvent;
import com.valentin.inventoryservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderLifecycleEventHandler {

    private final ProcessedEventsRepository inbox;
    private final ReservationRepository reservations;
    private final ReservationService service;
    private final Clock clock;

    @Transactional
    public void handle(OrderLifecycleEvent event) {
        inbox.lockOrder(event.orderId());
        if (inbox.tryInsert(event.eventId(), event.eventType(), event.orderId(), clock.instant()) == 0) {
            return;
        }
        var reservation = reservations.findByOrderId(event.orderId());
        boolean cancelled = OrderLifecycleEvent.CANCELLED.equals(event.eventType());
        if (reservation.isEmpty()) {
            if (cancelled) {
                log.debug("Order cancellation saved before reservation: orderId={}", event.orderId());
                return;
            }
            throw new IllegalStateException("Paid order reservation is not available yet");
        }
        var current = reservation.get().getStatus();
        var target = cancelled ? ReservationStatus.RELEASED : ReservationStatus.COMMITTED;
        if (current == target || cancelled && (current == ReservationStatus.FAILED || current == ReservationStatus.EXPIRED)) {
            return;
        }
        if (current != ReservationStatus.RESERVED) {
            throw new InvalidEventContractException("Order result conflicts with reservation status: " + current);
        }
        if (cancelled) {
            service.release(event.orderId());
        } else {
            service.commit(event.orderId());
        }
    }
}
