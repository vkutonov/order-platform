package com.valentin.paymentservice.domain.dictionary;

public enum OutboxEventStatus {
    NEW,
    PROCESSING,
    PUBLISHED,
    FAILED
}
