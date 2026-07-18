package com.valentin.inventoryservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "reservation_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_reservation_items_reservation_product",
                        columnNames = {"reservation_id", "product_id"}
                )
        }

)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationItemEntity {

    @GeneratedValue
    @UuidGenerator
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private ReservationEntity reservation;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public static ReservationItemEntity create(
            ReservationEntity reservation,
            UUID productId,
            int quantity
    ) {
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation mustn't be null");
        }

        if (productId == null) {
            throw new IllegalArgumentException("Product id mustn't be null");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        ReservationItemEntity item = new ReservationItemEntity();

        item.reservation = reservation;
        item.productId = productId;
        item.quantity = quantity;

        return item;
    }
}
