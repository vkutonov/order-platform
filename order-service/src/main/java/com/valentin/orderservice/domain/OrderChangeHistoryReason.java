package com.valentin.orderservice.domain;

public enum OrderChangeHistoryReason {
    ORDER_CREATED,
    INVENTORY_RESERVED,
    INVENTORY_RESERVATION_FAILED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    ORDER_CANCELLED_BY_USER,
    ORDER_EXPIRED
}
