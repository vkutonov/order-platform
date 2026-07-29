package com.valentin.inventoryservice.exception;

import java.util.UUID;

public class ReservationCommandConflictException extends InventoryServiceException {

    public ReservationCommandConflictException(UUID orderId) {
        super(
                ApiErrorCode.RESERVATION_COMMAND_CONFLICT,
                "Reservation already exists with different data: orderId=%s"
                        .formatted(orderId)
        );
    }
}
