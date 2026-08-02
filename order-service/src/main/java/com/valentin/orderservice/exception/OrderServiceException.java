package com.valentin.orderservice.exception;

public abstract class OrderServiceException extends RuntimeException {

    private final ApiErrorCode errorCode;

    protected OrderServiceException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
