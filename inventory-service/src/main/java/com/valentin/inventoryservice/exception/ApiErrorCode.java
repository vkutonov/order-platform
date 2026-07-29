package com.valentin.inventoryservice.exception;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESERVATION_NOT_EXPIRED(HttpStatus.CONFLICT),
    RESERVATION_COMMAND_CONFLICT(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
    INVALID_TRANSITION(HttpStatus.CONFLICT),
    DUPLICATE_RESERVATION_PRODUCT(HttpStatus.CONFLICT),
    CONCURRENT_STOCK_MODIFICATION(HttpStatus.CONFLICT),
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
