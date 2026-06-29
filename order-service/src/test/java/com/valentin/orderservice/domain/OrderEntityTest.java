package com.valentin.orderservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

        OrderEntity order = new OrderEntity();
        order.addItem(item1);
        order.addItem(item2);

        assertThat(order.getOrderItems()).containsExactly(item1, item2);

        assertThat(item1.getOrder()).isSameAs(order);
        assertThat(item2.getOrder()).isSameAs(order);

    }

    @Test
    public void recalculateTotalPrice_shouldSumAllItems() {
        OrderEntity order = new OrderEntity();

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
        assertThat(order.recalculateTotalPrice()).isEqualByComparingTo(new BigDecimal("400.00"));

    }

    @Test
    public void recalculateTotalPrice_whenNoItems_shouldReturnZero() {
        OrderEntity order = new OrderEntity();

        assertThat(order.recalculateTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }


}
