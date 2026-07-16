package com.valentin.inventoryservice.exception;

public class ProductNotFoundException extends InventoryServiceException {
    public ProductNotFoundException(String message) {
        super(ApiErrorCode.PRODUCT_NOT_FOUND, message);
    }
}
