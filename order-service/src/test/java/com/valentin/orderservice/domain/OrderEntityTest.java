package com.valentin.orderservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    private OrderEntity createOrder() {
        return OrderEntity.createOrderEntity(
                UUID.randomUUID(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                "RUB"
        );
    }

}
