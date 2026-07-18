package com.valentin.inventoryservice.domain;

import com.valentin.inventoryservice.exception.InsufficientStockException;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "inventory_items")
public class InventoryItemEntity {

    @UuidGenerator
    @GeneratedValue
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;


    public static InventoryItemEntity create(
            UUID productId,
            int quantityOnHand,
            Instant createdAt
    ) {
        InventoryItemEntity item = new InventoryItemEntity();

        item.productId = productId;
        item.quantityOnHand = quantityOnHand;
        item.reservedQuantity = 0;
        item.createdAt = createdAt;
        item.updatedAt = createdAt;

        return item;
    }

    public Integer getAvailableQuantity() {
        return quantityOnHand - reservedQuantity;
    }

    public void addStock(int quantity, Instant updatedAt) {
        quantityOnHand += quantity;
        this.updatedAt = updatedAt;
    }

    public void reserve(int quantity, Instant updatedAt) {
        int available = getAvailableQuantity();

        if (quantity > available) {
            throw new InsufficientStockException(("Available quantity must be greater than %s ," +
                    " but now available quantity = %s")
                    .formatted(quantity, available)
            );
        }

        reservedQuantity += quantity;
        this.updatedAt = updatedAt;
    }

    public void commitReservation(int quantity, Instant updatedAt) {

        if (reservedQuantity < quantity) {
            throw new InsufficientStockException(("Reserved quantity must be greater than %s ," +
                    " but now reserved quantity = %s")
                    .formatted(quantity, reservedQuantity)
            );
        }

        quantityOnHand -= quantity;
        reservedQuantity -= quantity;
        this.updatedAt = updatedAt;
    }

    public void release(int quantity, Instant updatedAt) {

        if (reservedQuantity < quantity) {
            throw new InsufficientStockException(("Reserved quantity must be greater than %s ," +
                    " but now reserved quantity = %s")
                    .formatted(quantity, reservedQuantity)
            );
        }

        reservedQuantity -= quantity;
        this.updatedAt = updatedAt;
    }
}
