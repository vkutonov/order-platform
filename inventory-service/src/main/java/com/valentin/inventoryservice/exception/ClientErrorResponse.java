package com.valentin.inventoryservice.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ClientErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<UUID> missingProductIds,
        List<FieldErrorResponse> fieldErrors
) {
}
