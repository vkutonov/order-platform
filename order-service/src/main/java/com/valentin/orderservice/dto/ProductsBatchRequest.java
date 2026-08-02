package com.valentin.orderservice.dto;

import java.util.Set;
import java.util.UUID;

public record ProductsBatchRequest(
        Set<UUID> productIds
) {
}
