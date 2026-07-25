package com.valentin.inventoryservice.exception;

import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;
import lombok.Getter;

import java.util.UUID;

@Getter
public class InvalidReservationStatusException extends InventoryServiceException{

    private final UUID orderId;
    private final ReservationStatus currentStatus;
    private final ReservationStatus requestedStatus;

    public InvalidReservationStatusException(
            UUID orderId,
            ReservationStatus currentStatus,
            ReservationStatus requestedStatus
    ) {
        super(ApiErrorCode.INVALID_TRANSITION,
                "Invalid order status transition: orderId=%s, currentStatus=%s, requestedStatus=%s"
                .formatted(orderId, currentStatus, requestedStatus));

        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;


    }
}

