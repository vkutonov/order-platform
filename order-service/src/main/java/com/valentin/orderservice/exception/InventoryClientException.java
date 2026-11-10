package com.valentin.orderservice.exception;

public final class InventoryClientException extends OrderServiceException {

    public InventoryClientException(ApiErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message);
        initCause(cause);
    }
}
