package com.valentin.orderservice.messaging.parser;

import com.valentin.orderservice.messaging.event.PaymentEvent;
import com.valentin.orderservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentEventParser {

    private final ObjectMapper objectMapper;

    public Optional<PaymentEvent> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidEventContractException("Payment event must not be empty");
        }
        try {
            var event = objectMapper.readTree(payload);
            if (event == null || !event.isObject() || !event.path("eventType").isString()) {
                throw new InvalidEventContractException("Payment event must be an object with eventType");
            }
            String type = event.path("eventType").asString();
            if ("PaymentRefunded".equals(type) || "PaymentRefundFailed".equals(type)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.treeToValue(event, PaymentEvent.class));
        } catch (JacksonException exception) {
            throw new InvalidEventContractException("Cannot parse PaymentEvent", exception);
        }
    }
}
