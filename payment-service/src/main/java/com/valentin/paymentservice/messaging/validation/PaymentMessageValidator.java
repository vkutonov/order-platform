package com.valentin.paymentservice.messaging.validation;

import com.valentin.paymentservice.messaging.event.*;
import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import com.valentin.paymentservice.messaging.exception.UnsupportedEventVersionException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMessageValidator {

    private final Validator validator;

    public void validateOrderEvent(OrderReadyForPaymentEvent event) {
        if (event == null) {
            throw new InvalidEventContractException("Order event must not be null");
        }
        var violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new InvalidEventContractException("Validation failed: " + violations);
        }
        if (event.eventVersion() != OrderReadyForPaymentEvent.VERSION) {
            throw new UnsupportedEventVersionException(event.eventVersion(), OrderReadyForPaymentEvent.VERSION);
        }
        if (!OrderReadyForPaymentEvent.TYPE.equals(event.eventType())) {
            throw new InvalidEventContractException("eventType must be " + OrderReadyForPaymentEvent.TYPE);
        }
    }

    public void validate(PaymentMessage message) {
        if (message == null) {
            throw new InvalidEventContractException("Payment message must not be null");
        }
        var violations = validator.validate(message);
        if (!violations.isEmpty()) {
            throw new InvalidEventContractException("Validation failed: " + violations);
        }
        if (message.eventVersion() != 1) {
            throw new UnsupportedEventVersionException(message.eventVersion(), 1);
        }
        String expectedType = switch (message) {
            case PaymentSucceeded ignored -> PaymentSucceeded.TYPE;
            case PaymentFailed ignored -> PaymentFailed.TYPE;
            case PaymentRefunded ignored -> PaymentRefunded.TYPE;
            case PaymentRefundFailed ignored -> PaymentRefundFailed.TYPE;
        };
        if (!expectedType.equals(message.eventType())) {
            throw new InvalidEventContractException("eventType must be " + expectedType);
        }
    }
}
