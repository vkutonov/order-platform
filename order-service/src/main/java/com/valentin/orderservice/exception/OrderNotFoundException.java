package com.valentin.orderservice.exception;

public final class OrderNotFoundException extends OrderServiceException {

    public OrderNotFoundException(String message) {
        super(ApiErrorCode.ORDER_NOT_FOUND, message);
    }
}
