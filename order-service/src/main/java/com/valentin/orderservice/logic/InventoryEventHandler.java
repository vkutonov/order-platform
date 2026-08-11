package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.ProcessedEventRepository;
import com.valentin.orderservice.messaging.event.InventoryEvent;
import com.valentin.orderservice.messaging.event.InventoryReservationFailedEvent;
import com.valentin.orderservice.messaging.event.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;


@Service
@RequiredArgsConstructor
public class InventoryEventHandler {

    private final ProcessedEventRepository processedEventRepository;
    private final OrderCommandService commandService;
    private final Clock clock;

    @Transactional
    public void handle(InventoryEvent event) {

        Instant timeNow = clock.instant();

        int insert = processedEventRepository.tryInsert(
                event.eventId(),
                event.eventType(),
                event.orderId(),
                timeNow
        );

        if (insert == 0) {
            return;
        }

        switch (event) {
            case InventoryReservedEvent reservedEvent ->
                    commandService.reserveInventory(reservedEvent.orderId());

            case InventoryReservationFailedEvent failedEvent ->
                    commandService.inventoryReservationFailed(
                            failedEvent.orderId(),
                            failedEvent.failureCode()
                    );
        }


    }

}
