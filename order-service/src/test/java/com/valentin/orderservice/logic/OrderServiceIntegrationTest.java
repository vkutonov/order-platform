package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-bookworm");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderHistoryRepository historyRepository;

    @Autowired
    private Clock clock;


    @Test
    void reserveInventory_shouldUpdateOrderAndSaveHistory() {

        UUID userId = UUID.randomUUID();

        OrderEntity order = OrderEntity.createOrderEntity(
                userId,
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                "RUB",
                clock.instant()
        );

        orderRepository.save(order);

        orderService.reserveInventory(order.getId());

        OrderEntity updatedOrder = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);

        assertThat(updatedOrder.getUpdatedAt()).isNotEqualTo(updatedOrder.getCreatedAt());

        List<OrderHistoryEntity> history = historyRepository
                .findOrderHistoryByIdByCreatedTimeAsc(order.getId());

        assertThat(history).hasSize(1);

        OrderHistoryEntity h = history.getFirst();

        assertThat(h.getOldStatus())
                .isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);

        assertThat(h.getNewStatus())
                .isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);

        assertThat(h.getReason())
                .isEqualTo(OrderChangeHistoryReason.INVENTORY_RESERVED);

        assertThat(h.getOrder().getId())
                .isEqualTo(order.getId());


    }






}
