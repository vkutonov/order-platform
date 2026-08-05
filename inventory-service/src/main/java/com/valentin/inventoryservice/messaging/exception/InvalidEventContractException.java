package com.valentin.inventoryservice.messaging.exception;

public class InvalidEventContractException extends EventContractException {

    public InvalidEventContractException(String message) {
        super(message);
    }

    public InvalidEventContractException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }

}
