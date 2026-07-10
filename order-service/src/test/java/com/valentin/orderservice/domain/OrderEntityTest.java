package com.valentin.orderservice.domain;

import com.valentin.orderservice.exception.InvalidOrderStatusTransitionException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrderEntityTest {

    @Test
    public void addItem_shouldAddItemsAndSetBackReferences() {

        UUID productId = UUID.randomUUID();

        OrderItemEntity item1 = OrderItemEntity.create(
                productId,
                "Test name1",
                new BigDecimal("150.00"),
                2
        );

        OrderItemEntity item2 = OrderItemEntity.create(
                productId,
                "Test name2",
                new BigDecimal("100.00"),
                1
        );

        OrderEntity order = createOrder();
        order.addItem(item1);
        order.addItem(item2);

        assertThat(order.getOrderItems()).containsExactly(item1, item2);

        assertThat(item1.getOrder()).isSameAs(order);
        assertThat(item2.getOrder()).isSameAs(order);

    }

    @Test
    public void recalculateTotalPrice_shouldSumAllItems() {
        OrderEntity order = createOrder();

        UUID productId = UUID.randomUUID();

        OrderItemEntity item1 = OrderItemEntity.create(
                productId,
                "Test name1",
                new BigDecimal("150.00"),
                2
        );

        OrderItemEntity item2 = OrderItemEntity.create(
                productId,
                "Test name2",
                new BigDecimal("100.00"),
                1
        );

        order.addItem(item1);
        order.addItem(item2);

        assertThat(order.getOrderItems()).containsExactly(item1, item2);
        assertThat(order.getTotalPrice()).isEqualByComparingTo(new BigDecimal("400.00"));

    }

    @Test
    public void recalculateTotalPrice_whenNoItems_shouldReturnZero() {
        OrderEntity order = createOrder();

        assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void markInventoryReserved_fromWaitingForInventory_shouldChangeStatusToWaitingForPayment() {
        OrderEntity order = createOrder(OrderStatus.WAITING_FOR_INVENTORY);

        order.markInventoryReserved(Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());
    }

    @Test
    void markPaymentSucceeded_fromWaitingForPayment_shouldChangeStatusToPaid() {
        OrderEntity order = createOrder(OrderStatus.WAITING_FOR_PAYMENT);

        order.markPaymentSucceeded(Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());
    }

    @Test
    void markPaymentFailed_fromWaitingForPayment_shouldChangeStatusToPaymentFailed() {
        OrderEntity order = createOrder(OrderStatus.WAITING_FOR_PAYMENT);

        order.markPaymentFailed(Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());
    }

    @Test
    void cancel_fromCreated_shouldChangeStatusToCancelled() {
        OrderEntity order = createOrder(OrderStatus.WAITING_FOR_INVENTORY);

        order.cancel(Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());
    }

    @Test
    void markPaymentSucceeded_fromCreated_shouldThrowException() {
        OrderEntity order = createOrder(OrderStatus.WAITING_FOR_INVENTORY);

        assertThatThrownBy(() -> order.markPaymentSucceeded(Instant.now()))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void cancel_fromPaid_shouldThrowException() {
        OrderEntity order = createOrder(OrderStatus.PAID);

        assertThatThrownBy(() -> order.cancel(Instant.now()))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    private OrderEntity createOrder() {
        return createOrder(OrderStatus.WAITING_FOR_INVENTORY);
    }

    private OrderEntity createOrder(OrderStatus status) {
        return OrderEntity.createOrderEntity(
                UUID.randomUUID(),
                new ArrayList<>(),
                status,
                "RUB",
                Instant.now()
        );
    }

}
