package com.valentin.orderservice.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "total_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    void setReferenceToOrder(OrderEntity order) {
        this.order = order;
    }

    public static OrderItemEntity create(
            UUID productId,
            String productName,
            BigDecimal unitPrice,
            String currency,
            Integer quantity
    ) {
        OrderItemEntity orderItem = new OrderItemEntity();
        orderItem.productId = productId;
        orderItem.productName = productName;
        orderItem.unitPrice = unitPrice;
        orderItem.currency = currency;
        orderItem.quantity = quantity;
        orderItem.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

        return orderItem;
    }
}
