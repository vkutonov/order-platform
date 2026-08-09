package com.valentin.orderservice.messaging.exception;



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
