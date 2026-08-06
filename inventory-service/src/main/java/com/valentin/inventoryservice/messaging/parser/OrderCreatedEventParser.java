package com.valentin.inventoryservice.messaging.parser;

import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OrderCreatedEventParser {

    private final ObjectMapper objectMapper;

    public OrderCreatedEvent parse(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    OrderCreatedEvent.class
            );
        } catch (JacksonException exception) {
            throw new InvalidEventContractException(
                    "Cannot parse OrderCreatedEvent",
                    exception
            );
        }
    }
}
