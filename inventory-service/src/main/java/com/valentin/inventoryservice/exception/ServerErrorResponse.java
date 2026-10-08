package com.valentin.inventoryservice.exception;

import java.time.Instant;

public record ServerErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {
}
