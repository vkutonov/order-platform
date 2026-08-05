package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.ProcessedEventsRepository;
import com.valentin.inventoryservice.mapper.OrderCreatedEventMapper;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class OrderCreatedEventHandler {

    private final ReservationService reservationService;
    private final ProcessedEventsRepository processedEventsRepository;
    private final OrderCreatedEventMapper mapper;
    private final Clock clock;

    @Transactional
    public void handle(OrderCreatedEvent event) {

        int insert = processedEventsRepository.tryInsert(
                event.eventId(),
                event.eventType(),
                event.orderId(),
                clock.instant()
        );

        if (insert == 0) {
            return;
        }

        reservationService.reserve(mapper.toCommand(event));


    }
}
