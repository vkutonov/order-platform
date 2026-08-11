package com.valentin.orderservice.domain.dictionary;

public enum OrderChangeHistoryReason {
    ORDER_CREATED,
    INVENTORY_RESERVED,
    INVENTORY_INSUFFICIENT_STOCK,
    INVENTORY_PRODUCT_INACTIVE,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    ORDER_CANCELLED_BY_USER,
    ORDER_EXPIRED
}
