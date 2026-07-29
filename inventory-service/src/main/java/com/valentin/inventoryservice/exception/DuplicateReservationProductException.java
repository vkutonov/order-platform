package com.valentin.inventoryservice.exception;

import java.util.UUID;

public final class DuplicateReservationProductException extends InventoryServiceException {

    public DuplicateReservationProductException(UUID productId) {
        super(
                ApiErrorCode.DUPLICATE_RESERVATION_PRODUCT,
                "Product already exists in reservation: productId=%s".formatted(productId)
        );
    }
}
