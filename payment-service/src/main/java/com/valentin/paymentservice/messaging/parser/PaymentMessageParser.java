package com.valentin.paymentservice.messaging.parser;

import com.valentin.paymentservice.messaging.event.PaymentMessage;
import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PaymentMessageParser {

    private final ObjectMapper objectMapper;

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
