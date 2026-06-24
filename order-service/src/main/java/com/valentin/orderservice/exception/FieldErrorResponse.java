package com.valentin.orderservice.exception;

public record FieldErrorResponse(
        String field,
        String message
) {
}
