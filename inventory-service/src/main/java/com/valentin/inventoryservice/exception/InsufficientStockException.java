package com.valentin.inventoryservice.exception;

public class InsufficientStockException extends InventoryServiceException {
    public InsufficientStockException(String message) {
        super(ApiErrorCode.INSUFFICIENT_STOCK, message);
    }
}
