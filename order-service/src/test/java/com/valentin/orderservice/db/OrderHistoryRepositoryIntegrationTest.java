package com.valentin.orderservice.db;

import com.valentin.orderservice.domain.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE )
@Testcontainers
public class OrderHistoryRepositoryIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderHistoryRepository historyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findOrderHistoryByIdByCreatedTimeAsc() {

        OrderEntity order1 = OrderEntity.createOrderEntity(
                UUID.randomUUID(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                new BigDecimal("100.00"),
                "RUB"
        );

        OrderEntity order2 = OrderEntity.createOrderEntity(
                UUID.randomUUID(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                new BigDecimal("150.00"),
                "RUB"
        );

        Instant createdAt = Instant.parse("2026-06-29T10:15:30Z");

        OrderHistoryEntity history1 = OrderHistoryEntity.create(
                order1,
                null,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderChangeHistoryReason.ORDER_CREATED,
                createdAt
        );

        OrderHistoryEntity history2 = OrderHistoryEntity.create(
                order2,
                null,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderChangeHistoryReason.ORDER_CREATED,
                createdAt
        );

        OrderHistoryEntity history3 = OrderHistoryEntity.create(
                order1,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderChangeHistoryReason.INVENTORY_RESERVED,
                createdAt.plusMillis(10)
        );

        orderRepository.save(order1);
        orderRepository.save(order2);

        historyRepository.save(history3);
        historyRepository.save(history1);
        historyRepository.save(history2);

        // чтобы запросы точно отправились, а не взялись из кэша
        entityManager.flush();
        entityManager.clear();

        List<OrderHistoryEntity> result = historyRepository
                .findOrderHistoryByIdByCreatedTimeAsc(order1.getId());

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(OrderHistoryEntity::getCreatedAt)
                .containsExactly(createdAt, createdAt.plusMillis(10));

        assertThat(result)
                .extracting(history -> history.getOrder().getId())
                .containsOnly(order1.getId());

    }


    @Test
    void findByOrderIdOrderByCreatedAtAsc_unknownOrderId_shouldReturnEmptyList() {
        UUID unknownOrderId = UUID.randomUUID();

        List<OrderHistoryEntity> result =
                historyRepository.findOrderHistoryByIdByCreatedTimeAsc(unknownOrderId);

        assertThat(result).isEmpty();
    }
}
