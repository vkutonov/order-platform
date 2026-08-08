package com.valentin.inventoryservice.domain.dictionary;

public enum OutboxEventStatus {
    NEW,
    PUBLISHED,
    PROCESSING,
    FAILED
}
