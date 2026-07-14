package com.valentin.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "reservation_items")
public class ReservationItemEntity {

    @GeneratedValue
    @UuidGenerator
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;


    public static ReservationItemEntity create(
            UUID reservationId,
            UUID productId,
            String productName,
            BigDecimal unitPrice,
            String currency,
            int quantity,
            BigDecimal totalPrice
    ) {
        ReservationItemEntity item = new ReservationItemEntity();

        item.reservationId = reservationId;
        item.productId = productId;
        item.productName = productName;
        item.unitPrice = unitPrice;
        item.currency = currency;
        item.quantity = quantity;
        item.totalPrice = totalPrice;

        return item;
    }
}
