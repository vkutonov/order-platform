package com.valentin.inventoryservice.exception;

public record FieldErrorResponse(
        String field,
        String message
) {
}
