package com.valentin.inventoryservice.mapper;

import com.valentin.inventoryservice.dto.CreateReservationCommand;
import com.valentin.inventoryservice.dto.ReservationItemCommand;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedEventMapper {

    public CreateReservationCommand toCommand(
            OrderCreatedEvent event
    ) {
        return new CreateReservationCommand(
                event.orderId(),
                event.userId(),
                event.items().stream()
                        .map(item -> new ReservationItemCommand(
                                item.productId(),
                                item.quantity()
                        ))
                        .toList()
        );
    }
}
