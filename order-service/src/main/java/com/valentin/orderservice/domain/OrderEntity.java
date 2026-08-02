package com.valentin.orderservice.domain;

import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.exception.InvalidOrderStatusTransitionException;
import com.valentin.orderservice.exception.MixedOrderCurrenciesException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class OrderEntity {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.WAITING_FOR_INVENTORY, Set.of(OrderStatus.WAITING_FOR_PAYMENT, OrderStatus.CANCELLED),
            OrderStatus.WAITING_FOR_PAYMENT, Set.of(OrderStatus.PAID, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_FAILED, Set.of(OrderStatus.WAITING_FOR_PAYMENT, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> orderItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "version")
    @Version
    private Long version;

    public void recalculateTotalPrice() {
        this.totalPrice = orderItems.stream()
                .map(OrderItemEntity::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addItem(OrderItemEntity item) {
        if (!Objects.equals(currency, item.getCurrency())) {
            throw new MixedOrderCurrenciesException(currency, item.getCurrency());
        }

        orderItems.add(item);
        item.setReferenceToOrder(this);
        recalculateTotalPrice();
    }

    public static OrderEntity createOrderEntity(
            UUID userId,
            List<OrderItemEntity> items,
            OrderStatus status,
            String currency,
            Instant timeNow
    ) {
        OrderEntity order = new OrderEntity();

        order.userId = userId;
        order.status = status;
        order.currency = currency;
        order.createdAt = timeNow;
        order.updatedAt = timeNow;
        order.totalPrice = BigDecimal.ZERO;
        items.forEach(order::addItem);

        return order;
    }

    public void markInventoryReserved(Instant now) {
        changeStatus(OrderStatus.WAITING_FOR_PAYMENT, now);
    }

    public void markInventoryReservationFailed(Instant now) {
        changeStatus(OrderStatus.CANCELLED, now);
    }

    public void markPaymentSucceeded(Instant now) {
        changeStatus(OrderStatus.PAID, now);
    }

    public void markPaymentFailed(Instant now) {
        changeStatus(OrderStatus.PAYMENT_FAILED, now);
    }

    public void cancel(Instant now) {
        changeStatus(OrderStatus.CANCELLED, now);
    }


    private void changeStatus(
            OrderStatus status,
            Instant timeNow
    ) {

        if (status == null) {
            throw new IllegalArgumentException("New order status must not be null");
        }

        Set<OrderStatus> allowedStatuses = ALLOWED_TRANSITIONS.getOrDefault(this.status, Set.of());

        if (!allowedStatuses.contains(status)) {
            throw new InvalidOrderStatusTransitionException(this.id, this.status, status);
        }

        this.status = status;
        this.updatedAt = timeNow;

    }
}
