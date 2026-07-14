package com.valentin.inventoryservice.domain;

import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "reservations")
public class ReservationEntity {

    @GeneratedValue
    @UuidGenerator
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public static ReservationEntity create(
            UUID orderId,
            UUID userId,
            ReservationStatus status,
            String failureReason,
            Instant expiresAt,
            Instant createdAt
    ) {
        ReservationEntity reservation = new ReservationEntity();

        reservation.orderId = orderId;
        reservation.userId = userId;
        reservation.status = status;
        reservation.failureReason = failureReason;
        reservation.expiresAt = expiresAt;
        reservation.createdAt = createdAt;
        reservation.updatedAt = createdAt;

        return reservation;
    }
}
