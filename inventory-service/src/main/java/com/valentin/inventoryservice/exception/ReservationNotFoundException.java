package com.valentin.inventoryservice.exception;

import java.util.UUID;

public final class ReservationNotFoundException extends InventoryServiceException {

    public ReservationNotFoundException(UUID orderId) {
        super(
                ApiErrorCode.RESERVATION_NOT_FOUND,
                "Reservation not found: orderId=%s".formatted(orderId)
        );
    }
}
