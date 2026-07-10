package com.valentin.orderservice.domain.dictionary;

public enum OutboxEventStatus {
    NEW,
    PROCESSING,
    PUBLISHED,
    FAILED
}
