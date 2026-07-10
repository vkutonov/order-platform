package com.valentin.orderservice.mapper;

import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.OrderItemEntity;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.dto.OrderHistoryResponse;
import com.valentin.orderservice.dto.OrderResponse;
import com.valentin.orderservice.dto.OrderSummaryResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

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
                "RUB",
                createdAt
        );
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        ReflectionTestUtils.setField(order, "updatedAt", updatedAt);

        OrderItemEntity item = OrderItemEntity.create(
                productId,
                "Keyboard",
                new BigDecimal("10.50"),
                2
        );
        ReflectionTestUtils.setField(item, "id", itemId);
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

        OrderEntity order = OrderEntity.createOrderEntity(
                UUID.randomUUID(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                "RUB",
                createdAt
        );
        ReflectionTestUtils.setField(order, "id", orderId);

        OrderHistoryEntity history = OrderHistoryEntity.create(
                order,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderChangeHistoryReason.INVENTORY_RESERVED,
                createdAt
        );
        ReflectionTestUtils.setField(history, "id", historyId);

        OrderHistoryResponse response = mapper.toOrderHistoryResponse(history);

        assertThat(response.id()).isEqualTo(historyId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.oldStatus()).isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);
        assertThat(response.newStatus()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(response.reason()).isEqualTo("INVENTORY_RESERVED");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void toOrderSummaryResponse_shouldMapOrder() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-29T10:15:30Z");
        Instant updatedAt = Instant.parse("2026-06-29T10:20:30Z");

        OrderEntity order = OrderEntity.createOrderEntity(
                userId,
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_PAYMENT,
                "RUB",
                createdAt
        );
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        ReflectionTestUtils.setField(order, "updatedAt", updatedAt);
        order.addItem(OrderItemEntity.create(
                UUID.randomUUID(),
                "Keyboard",
                new BigDecimal("46.00"),
                1
        ));

        OrderSummaryResponse response = mapper.toOrderSummaryResponse(order);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(response.totalPrice()).isEqualByComparingTo("46.00");
        assertThat(response.currency()).isEqualTo("RUB");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }
}
