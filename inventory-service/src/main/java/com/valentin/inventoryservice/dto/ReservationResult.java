package com.valentin.inventoryservice.dto;

import com.valentin.inventoryservice.domain.dictionary.ReservationFailureCode;
import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReservationResult(
        UUID reservationId,
        UUID orderId,
        ReservationStatus status,
        Instant expiresAt,
        ReservationFailureCode failureCode
) {
    public boolean successful() {
        return ReservationStatus.RESERVED.equals(status);
    }

    public static ReservationResult reserved(
            UUID reservationId,
            UUID orderId,
            Instant expiresAt
    ) {
        return new ReservationResult(
                reservationId,
                orderId,
                ReservationStatus.RESERVED,
                expiresAt,
                null
        );
    }

    public static ReservationResult failed(
            UUID reservationId,
            UUID orderId,
            ReservationFailureCode failureCode
    ) {
        return new ReservationResult(
                reservationId,
                orderId,
                ReservationStatus.FAILED,
                null,
                Objects.requireNonNull(failureCode)
        );
    }
}
