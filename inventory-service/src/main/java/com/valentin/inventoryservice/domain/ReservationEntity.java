package com.valentin.inventoryservice.domain;

import com.valentin.inventoryservice.domain.dictionary.ReservationFailureCode;
import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;
import com.valentin.inventoryservice.exception.ApiErrorCode;
import com.valentin.inventoryservice.exception.DuplicateReservationProductException;
import com.valentin.inventoryservice.exception.InvalidReservationStatusException;
import com.valentin.inventoryservice.exception.ReservationNotExpiredException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.*;

@Getter
@Entity
@Table(
        name = "reservations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservations_order_id",
                        columnNames = "order_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationEntity {

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReservationStatus.PENDING,
            Set.of(
                    ReservationStatus.RESERVED,
                    ReservationStatus.FAILED
            ),

            ReservationStatus.RESERVED,
            Set.of(
                    ReservationStatus.COMMITTED,
                    ReservationStatus.RELEASED,
                    ReservationStatus.EXPIRED
            ),

            ReservationStatus.FAILED, Set.of(),
            ReservationStatus.COMMITTED, Set.of(),
            ReservationStatus.RELEASED, Set.of(),
            ReservationStatus.EXPIRED, Set.of()
    );

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

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 50)
    private ReservationFailureCode failureCode;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "reservation",
            cascade = {
                    CascadeType.PERSIST,
                    CascadeType.MERGE
            }
    )
    private List<ReservationItemEntity> items = new ArrayList<>();


    public List<ReservationItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }

    public static ReservationEntity create(
            UUID orderId,
            UUID userId,
            Instant expiresAt,
            Instant createdAt
    ) {
        ReservationEntity reservation = new ReservationEntity();

        reservation.orderId = orderId;
        reservation.userId = userId;
        reservation.status = ReservationStatus.PENDING;
        reservation.failureCode = null;
        reservation.expiresAt = expiresAt;
        reservation.createdAt = createdAt;
        reservation.updatedAt = createdAt;


        return reservation;
    }

    public void addItem(
            UUID productId,
            int quantity
    ) {
        boolean duplicate = items.stream().anyMatch(
                item -> item.getProductId().equals(productId)
        );

        if (duplicate) {
            throw new DuplicateReservationProductException(
                    ApiErrorCode.DUPLICATE_RESERVATION_PRODUCT,
                    productId
            );
        }

        items.add(
                ReservationItemEntity.create(
                        this,
                        productId,
                        quantity
                )
        );
    }

    public void markReserved(Instant now) {
        transitionTo(ReservationStatus.RESERVED, now);
    }

    public void markFailed(
            ReservationFailureCode failureCode,
            Instant now
    ) {
        this.failureCode = Objects.requireNonNull(
                failureCode,
                "Failure code must not be null"
        );
        transitionTo(ReservationStatus.FAILED, now);
    }

    public void commit(Instant now) {
        transitionTo(ReservationStatus.COMMITTED, now);
    }

    public void release(Instant now) {
        transitionTo(ReservationStatus.RELEASED, now);
    }

    public void expire(Instant now) {
        validateTransition(ReservationStatus.EXPIRED);

        if (expiresAt == null || now.isBefore(expiresAt)) {
            throw new ReservationNotExpiredException(
                    orderId,
                    expiresAt,
                    now
            );
        }

        applyTransition(ReservationStatus.EXPIRED, now);
    }

    private void transitionTo(
            ReservationStatus targetStatus,
            Instant updatedAt
    ) {
        validateTransition(targetStatus);
        applyTransition(targetStatus, updatedAt);
    }

    private void validateTransition(ReservationStatus targetStatus) {
        Set<ReservationStatus> allowed =
                ALLOWED_TRANSITIONS.getOrDefault(status, Set.of());

        if (!allowed.contains(targetStatus)) {
            throw new InvalidReservationStatusException(
                    id,
                    status,
                    targetStatus
            );
        }
    }

    private void applyTransition(
            ReservationStatus targetStatus,
            Instant updatedAt
    ) {
        status = targetStatus;
        this.updatedAt = updatedAt;
    }
}
