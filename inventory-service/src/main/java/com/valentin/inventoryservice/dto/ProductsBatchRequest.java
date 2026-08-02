package com.valentin.inventoryservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record ProductsBatchRequest (
        @NotEmpty
        Set<@NotNull UUID> productIds
) {
}
