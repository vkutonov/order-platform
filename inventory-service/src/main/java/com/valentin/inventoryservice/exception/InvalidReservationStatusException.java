package com.valentin.inventoryservice.exception;

import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;

import java.util.UUID;

public final class InvalidReservationStatusException extends InventoryServiceException {

    public InvalidReservationStatusException(
            UUID reservationId,
            ReservationStatus currentStatus,
            ReservationStatus requestedStatus
    ) {
        super(
                ApiErrorCode.INVALID_TRANSITION,
                "Invalid reservation status transition: reservationId=%s, currentStatus=%s, requestedStatus=%s"
                        .formatted(reservationId, currentStatus, requestedStatus)
        );
    }
}
