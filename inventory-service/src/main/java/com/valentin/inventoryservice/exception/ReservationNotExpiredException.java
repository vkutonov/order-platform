package com.valentin.inventoryservice.exception;

import java.time.Instant;
import java.util.UUID;

public final class ReservationNotExpiredException extends InventoryServiceException {

    public ReservationNotExpiredException(
            UUID orderId,
            Instant expiresAt,
            Instant now
    ) {
        super(
                ApiErrorCode.RESERVATION_NOT_EXPIRED,
                "Reservation has not expired: orderId=%s, expiresAt=%s, now=%s"
                        .formatted(orderId, expiresAt, now)
        );
    }
}
