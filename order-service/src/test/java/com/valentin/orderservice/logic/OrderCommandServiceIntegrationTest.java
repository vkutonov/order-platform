package com.valentin.orderservice.logic;

import com.valentin.orderservice.config.TimeConfig;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        OrderCommandService.class,
        TimeConfig.class
})
@Testcontainers
public class OrderCommandServiceIntegrationTest {

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
    private Clock clock;

    @Autowired
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OrderMapper orderMapper;

    @MockitoBean
    private ObjectMapper objectMapper;

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

        orderCommandService.reserveInventory(order.getId());

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
