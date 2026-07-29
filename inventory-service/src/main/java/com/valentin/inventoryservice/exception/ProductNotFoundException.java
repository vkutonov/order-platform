package com.valentin.inventoryservice.exception;

import java.util.Collection;
import java.util.UUID;

public final class ProductNotFoundException extends InventoryServiceException {

    public ProductNotFoundException(UUID productId) {
        super(
                ApiErrorCode.PRODUCT_NOT_FOUND,
                "Product not found: productId=%s".formatted(productId)
        );
    }

    public ProductNotFoundException(Collection<UUID> productIds) {
        super(
                ApiErrorCode.PRODUCT_NOT_FOUND,
                "Products not found: productIds=%s".formatted(productIds)
        );
    }
}
