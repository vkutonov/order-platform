package com.valentin.inventoryservice.domain;


import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "products")
public class ProductEntity {

    @GeneratedValue
    @UuidGenerator
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public static ProductEntity create(
            String name,
            String description,
            BigDecimal unitPrice,
            String currency,
            ProductStatus status,
            Instant createdAt
    ) {
        ProductEntity product = new ProductEntity();

        product.name = name;
        product.description = description;
        product.unitPrice = unitPrice;
        product.currency = currency;
        product.status = status;
        product.createdAt = createdAt;
        product.updatedAt = createdAt;

        return product;
    }
}
