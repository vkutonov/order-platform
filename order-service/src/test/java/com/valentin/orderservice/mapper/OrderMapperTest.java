package com.valentin.orderservice.mapper;

import com.valentin.orderservice.domain.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.OrderItemEntity;
import com.valentin.orderservice.domain.OrderStatus;
import com.valentin.orderservice.dto.OrderHistoryResponse;
import com.valentin.orderservice.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

    @Test
    void toOrderResponse_shouldMapOrderWithItems() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-29T10:15:30Z");
        Instant updatedAt = Instant.parse("2026-06-29T10:20:30Z");

        OrderEntity order = OrderEntity.createOrderEntity(
                userId,
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                new BigDecimal("21.00"),
                "RUB"
        );
        order.setId(orderId);
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(updatedAt);

        OrderItemEntity item = OrderItemEntity.create(
                productId,
                "Keyboard",
                new BigDecimal("10.50"),
                2
        );
        item.setId(itemId);
        order.addItem(item);

        OrderResponse response = mapper.toOrderResponse(order);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);
        assertThat(response.totalPrice()).isEqualByComparingTo("21.00");
        assertThat(response.currency()).isEqualTo("RUB");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.orderItems()).hasSize(1);
        assertThat(response.orderItems().getFirst().id()).isEqualTo(itemId);
        assertThat(response.orderItems().getFirst().productId()).isEqualTo(productId);
        assertThat(response.orderItems().getFirst().productName()).isEqualTo("Keyboard");
        assertThat(response.orderItems().getFirst().unitPrice()).isEqualByComparingTo("10.50");
        assertThat(response.orderItems().getFirst().quantity()).isEqualTo(2);
        assertThat(response.orderItems().getFirst().totalPrice()).isEqualByComparingTo("21.00");
    }

    @Test
    void toOrderHistoryResponse_shouldMapNestedOrderIdAndReasonName() {
        UUID orderId = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-29T10:15:30Z");

        OrderEntity order = new OrderEntity();
        order.setId(orderId);

        OrderHistoryEntity history = OrderHistoryEntity.create(
                order,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderChangeHistoryReason.INVENTORY_RESERVED,
                createdAt
        );
        history.setId(historyId);

        OrderHistoryResponse response = mapper.toOrderHistoryResponse(history);

        assertThat(response.id()).isEqualTo(historyId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.oldStatus()).isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);
        assertThat(response.newStatus()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(response.reason()).isEqualTo("INVENTORY_RESERVED");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
