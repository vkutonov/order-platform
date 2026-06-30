package com.valentin.orderservice.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_status_history")
public class OrderHistoryEntity {
    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status")
    private OrderStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private OrderStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    private OrderChangeHistoryReason reason;

    @Column(name = "created_at")
    private Instant createdAt;

    public static OrderHistoryEntity create(
            OrderEntity order,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            OrderChangeHistoryReason reason,
            Instant time
    ) {
        OrderHistoryEntity orderHistoryEntity = new OrderHistoryEntity();
        orderHistoryEntity.setCreatedAt(time);
        orderHistoryEntity.setOrder(order);
        orderHistoryEntity.setOldStatus(oldStatus);
        orderHistoryEntity.setNewStatus(newStatus);
        orderHistoryEntity.setReason(reason);

        return orderHistoryEntity;
    }
}
