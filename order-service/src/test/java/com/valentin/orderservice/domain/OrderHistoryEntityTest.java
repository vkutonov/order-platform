package com.valentin.orderservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderHistoryEntityTest {

    @Test
    public void create_shouldPopulateHistoryFields() {
        OrderEntity order = new OrderEntity();
        Instant createdAt = Instant.parse("2026-06-29T10:15:30Z");

        OrderHistoryEntity history = OrderHistoryEntity.create(
                order,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderChangeHistoryReason.ORDER_CREATED,
                createdAt
        );

        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getOldStatus()).isNull();
        assertThat(history.getNewStatus()).isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);
        assertThat(history.getReason()).isEqualTo(OrderChangeHistoryReason.ORDER_CREATED);
        assertThat(history.getCreatedAt()).isEqualTo(createdAt);
    }
}
