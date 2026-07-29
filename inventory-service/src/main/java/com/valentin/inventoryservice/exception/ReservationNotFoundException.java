package com.valentin.inventoryservice.exception;

import java.util.UUID;

public class ReservationNotFoundException extends InventoryServiceException {
    public ReservationNotFoundException(UUID orderId) {
        super(
                ApiErrorCode.RESERVATION_NOT_FOUND,
                "Reservation not found for orderId=%s".formatted(orderId)
        );
    }
}
