package com.valentin.paymentservice.messaging.validation;

import com.valentin.paymentservice.messaging.event.*;
import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import com.valentin.paymentservice.messaging.exception.UnsupportedEventVersionException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class PaymentMessageValidator {

    private final Validator validator;

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
            case CreatePaymentRequested ignored -> CreatePaymentRequested.TYPE;
            case RefundPaymentRequested ignored -> RefundPaymentRequested.TYPE;
            case PaymentSessionCreated ignored -> PaymentSessionCreated.TYPE;
            case PaymentSucceeded ignored -> PaymentSucceeded.TYPE;
            case PaymentFailed ignored -> PaymentFailed.TYPE;
            case PaymentRefunded ignored -> PaymentRefunded.TYPE;
            case PaymentRefundFailed ignored -> PaymentRefundFailed.TYPE;
        };
        if (!expectedType.equals(message.eventType())) {
            throw new InvalidEventContractException("eventType must be " + expectedType);
        }
        if (message instanceof CreatePaymentRequested command
                && !command.expiresAt().isAfter(command.occurredAt())) {
            throw new InvalidEventContractException("Reservation expiry must be after command creation");
        }
        if (message instanceof PaymentSessionCreated session) {
            try {
                URI uri = URI.create(session.checkoutUrl());
                if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                    throw new IllegalArgumentException("checkoutUrl must be an absolute HTTP URL");
                }
            } catch (IllegalArgumentException exception) {
                throw new InvalidEventContractException("Invalid checkoutUrl", exception);
            }
        }
    }
}
