package com.valentin.orderservice.messaging.exception;

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
