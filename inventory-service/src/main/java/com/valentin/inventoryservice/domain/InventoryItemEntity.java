package com.valentin.inventoryservice.domain;

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
            int reservedQuantity,
            Instant createdAt
    ) {
        InventoryItemEntity item = new InventoryItemEntity();

        item.productId = productId;
        item.quantityOnHand = quantityOnHand;
        item.reservedQuantity = reservedQuantity;
        item.createdAt = createdAt;
        item.updatedAt = createdAt;

        return item;
    }

}
