package com.valentin.inventoryservice.messaging.validation;

import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedItem;
import com.valentin.inventoryservice.messaging.exception.InvalidEventContractException;
import com.valentin.inventoryservice.messaging.exception.UnsupportedEventVersionException;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedEventValidator {

    private static final String SUPPORTED_EVENT_TYPE = "OrderCreatedEvent";
    private static final int SUPPORTED_VERSION = 1;

    public void validate(OrderCreatedEvent event) {
        if (event == null) {
            throw new InvalidEventContractException(
                    "event must not be null"
            );
        }

        if (event.eventId() == null) {
            throw new InvalidEventContractException(
                    "eventId must not be null"
            );
        }

        if (event.userId() == null) {
            throw new InvalidEventContractException(
                    "userId must not be null"
            );
        }

        if (!SUPPORTED_EVENT_TYPE.equals(event.eventType())) {
            throw new InvalidEventContractException(
                    "eventType must be %s".formatted(SUPPORTED_EVENT_TYPE)
            );
        }

        if (event.eventVersion() != SUPPORTED_VERSION) {
            throw new UnsupportedEventVersionException(
                    event.eventVersion(),
                    SUPPORTED_VERSION
            );
        }

        if (event.orderId() == null) {
            throw new InvalidEventContractException(
                    "orderId must not be null"
            );
        }

        if (event.items() == null
                || event.items().isEmpty()) {
            throw new InvalidEventContractException(
                    "items must not be empty"
            );
        }

        for (int index = 0; index < event.items().size(); index++) {
            OrderCreatedItem item = event.items().get(index);

            if (item == null) {
                throw new InvalidEventContractException(
                        "items[%s] must not be null".formatted(index)
                );
            }

            if (item.productId() == null) {
                throw new InvalidEventContractException(
                        "items[%s].productId must not be null".formatted(index)
                );
            }

            if (item.quantity() == null || item.quantity() <= 0) {
                throw new InvalidEventContractException(
                        "items[%s].quantity must be positive".formatted(index)
                );
            }
        }

        if (event.occurredAt() == null) {
            throw new InvalidEventContractException(
                    "occurredAt must not be null"
            );
        }
    }
}
