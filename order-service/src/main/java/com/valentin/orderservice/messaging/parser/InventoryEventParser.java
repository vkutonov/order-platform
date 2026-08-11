package com.valentin.orderservice.messaging.parser;

import com.valentin.orderservice.messaging.event.InventoryEvent;
import com.valentin.orderservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class InventoryEventParser {

    private final ObjectMapper objectMapper;

    public InventoryEvent parse(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    InventoryEvent.class
            );
        } catch (JacksonException exception) {
            throw new InvalidEventContractException(
                    "Cannot parse InventoryEvent",
                    exception
            );
        }
    }
}
