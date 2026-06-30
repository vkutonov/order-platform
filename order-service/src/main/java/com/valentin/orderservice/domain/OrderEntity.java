package com.valentin.orderservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> orderItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "total_price", precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "version")
    @Version
    private Long version;

    public BigDecimal recalculateTotalPrice() {
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemEntity item : orderItems) {
            totalPrice = totalPrice.add(item.getTotalPrice());
        }

        return totalPrice;
    }

    public void addItem(OrderItemEntity item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public static OrderEntity createOrderEntity(
        UUID userId,
        List<OrderItemEntity> items,
        OrderStatus status,
        BigDecimal totalPrice,
        String currency
    ) {
        OrderEntity order = new OrderEntity();

        Instant timeNow = Instant.now();

        order.setUserId(userId);
        order.setOrderItems(items);
        order.setTotalPrice(totalPrice);
        order.setStatus(status);
        order.setCurrency(currency);
        order.setCreatedAt(timeNow);
        order.setUpdatedAt(timeNow);

        return order;
    }
}
