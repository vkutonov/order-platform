package com.valentin.orderservice.exception;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PRODUCT_UNAVAILABLE(HttpStatus.CONFLICT),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.CONFLICT),
    MIXED_ORDER_CURRENCIES(HttpStatus.UNPROCESSABLE_CONTENT),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER_VALUE(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
