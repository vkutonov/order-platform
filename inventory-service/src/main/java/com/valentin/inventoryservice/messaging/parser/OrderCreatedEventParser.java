package com.valentin.inventoryservice.messaging.parser;

import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderCreatedEventParser {

    private final ObjectMapper objectMapper;

    public Optional<OrderCreatedEvent> parse(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            if (event == null || !event.isObject()) {
                throw new InvalidEventContractException("Event must be a JSON object");
            }

            // This order event is consumed by payment-service, not inventory-service.
            if ("OrderReadyForPaymentEvent".equals(event.path("eventType").asString())) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.treeToValue(event, OrderCreatedEvent.class));
        } catch (JacksonException exception) {
            throw new InvalidEventContractException(
                    "Cannot parse OrderCreatedEvent",
                    exception
            );
        }
    }
}
