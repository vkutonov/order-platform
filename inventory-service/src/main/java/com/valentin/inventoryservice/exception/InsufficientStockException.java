package com.valentin.inventoryservice.exception;

import java.util.UUID;

public final class InsufficientStockException extends InventoryServiceException {

    private InsufficientStockException(String message) {
        super(ApiErrorCode.INSUFFICIENT_STOCK, message);
    }

    public static InsufficientStockException forAvailableStock(
            UUID productId,
            int requestedQuantity,
            int availableQuantity
    ) {
        return new InsufficientStockException(
                "Insufficient available stock: productId=%s, requested=%d, available=%d"
                        .formatted(productId, requestedQuantity, availableQuantity)
        );
    }

    public static InsufficientStockException forReservedStock(
            UUID productId,
            int requestedQuantity,
            int reservedQuantity
    ) {
        return new InsufficientStockException(
                "Insufficient reserved stock: productId=%s, requested=%d, reserved=%d"
                        .formatted(productId, requestedQuantity, reservedQuantity)
        );
    }
}
