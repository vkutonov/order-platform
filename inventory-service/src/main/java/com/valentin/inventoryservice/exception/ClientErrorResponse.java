package com.valentin.inventoryservice.exception;

import java.time.Instant;
import java.util.List;

public record ClientErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldErrorResponse> fieldErrors
) {
}
