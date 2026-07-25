package com.valentin.inventoryservice.exception;

import java.util.UUID;

public class DuplicateReservationProductException extends InventoryServiceException {
    public DuplicateReservationProductException(ApiErrorCode code, UUID productId) {
        super(code, "Product already exists in reservation: productId = %s"
                .formatted(productId)
        );
    }
}
