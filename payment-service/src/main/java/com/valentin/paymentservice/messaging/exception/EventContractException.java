package com.valentin.paymentservice.messaging.exception;

public abstract class EventContractException extends RuntimeException {

    protected EventContractException(String message) {
        super(message);
    }

    protected EventContractException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
