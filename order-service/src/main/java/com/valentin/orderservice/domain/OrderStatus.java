package com.valentin.orderservice.domain;

public enum OrderStatus {
    WAITING_FOR_INVENTORY,
    WAITING_FOR_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    CANCELLED
}
