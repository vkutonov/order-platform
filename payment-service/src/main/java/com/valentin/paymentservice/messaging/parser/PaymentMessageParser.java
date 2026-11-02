package com.valentin.paymentservice.messaging.parser;

import com.valentin.paymentservice.messaging.event.PaymentMessage;
import com.valentin.paymentservice.messaging.event.OrderReadyForPaymentEvent;
import com.valentin.paymentservice.messaging.event.OrderLifecycleEvent;
import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentMessageParser {

    private final ObjectMapper objectMapper;

    public Optional<OrderLifecycleEvent> parseCancellation(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidEventContractException("Order event must not be empty");
        }
        try {
            var event = objectMapper.readTree(payload);
            if (event != null && event.isObject()
                    && OrderLifecycleEvent.CANCELLED.equals(event.path("eventType").asString())) {
                return Optional.of(objectMapper.treeToValue(event, OrderLifecycleEvent.class));
            }
            return Optional.empty();
        } catch (JacksonException exception) {
            throw new InvalidEventContractException("Cannot parse order cancellation", exception);
        }
    }

    public Optional<OrderReadyForPaymentEvent> parseOrderEvent(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidEventContractException("Order event must not be empty");
        }
        try {
            var event = objectMapper.readTree(payload);
            if (event == null || !event.isObject() || !event.path("eventType").isString()
                    || event.path("eventType").asString().isBlank()) {
                throw new InvalidEventContractException("Order event must be an object with eventType");
            }
            if (!OrderReadyForPaymentEvent.TYPE.equals(event.path("eventType").asString())) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.treeToValue(event, OrderReadyForPaymentEvent.class));
        } catch (JacksonException exception) {
            throw new InvalidEventContractException("Cannot parse order event", exception);
        }
    }

    public PaymentMessage parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidEventContractException("Payment message must not be empty");
        }
        try {
            PaymentMessage message = objectMapper.readValue(payload, PaymentMessage.class);
            if (message == null) {
                throw new InvalidEventContractException("Payment message must not be null");
            }
            return message;
        } catch (JacksonException exception) {
            throw new InvalidEventContractException("Cannot parse PaymentMessage", exception);
        }
    }
}
