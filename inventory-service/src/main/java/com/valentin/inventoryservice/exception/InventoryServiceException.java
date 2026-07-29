package com.valentin.inventoryservice.exception;

public abstract class InventoryServiceException extends RuntimeException {

    private final ApiErrorCode errorCode;

    protected InventoryServiceException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
