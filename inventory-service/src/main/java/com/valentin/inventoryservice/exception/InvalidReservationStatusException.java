package com.valentin.inventoryservice.exception;

import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;
import lombok.Getter;

import java.util.UUID;

@Getter
public class InvalidReservationStatusException extends InventoryServiceException{

    private final UUID reservationId;
    private final ReservationStatus currentStatus;
    private final ReservationStatus requestedStatus;

    public InvalidReservationStatusException(
            UUID reservationId,
            ReservationStatus currentStatus,
            ReservationStatus requestedStatus
    ) {
        super(ApiErrorCode.INVALID_TRANSITION,
                "Invalid order status transition: reservationId=%s, currentStatus=%s, requestedStatus=%s"
                .formatted(reservationId, currentStatus, requestedStatus));

        this.reservationId = reservationId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;


    }
}

