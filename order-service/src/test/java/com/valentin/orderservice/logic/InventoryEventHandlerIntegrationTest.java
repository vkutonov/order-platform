package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.ProcessedEventRepository;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.messaging.event.InventoryReservedEvent;
import com.valentin.orderservice.messaging.outbox.OutboxEventPoller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class InventoryEventHandlerIntegrationTest {

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-bookworm");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private InventoryEventHandler handler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private OrderHistoryRepository orderHistoryRepository;

    @MockitoBean
    private OutboxEventPoller outboxEventPoller;

    @Test
    void handle_whenHistorySaveFails_shouldRollbackOrderAndProcessedEvent() {
        Instant createdAt = OCCURRED_AT.minusSeconds(60);

        OrderEntity order = orderRepository.saveAndFlush(OrderEntity.createOrderEntity(
                UUID.randomUUID(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                "RUB",
                createdAt
        ));

        InventoryReservedEvent event = new InventoryReservedEvent(
                EVENT_ID,
                "InventoryReservedEvent",
                1,
                order.getId(),
                RESERVATION_ID,
                Map.of("source", "inventory-service"),
                OCCURRED_AT
        );

        when(orderHistoryRepository.save(any()))
                .thenThrow(new RuntimeException("History save failed"));

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("History save failed");

        assertThat(processedEventRepository.existsById(EVENT_ID)).isFalse();
        assertThat(orderRepository.findById(order.getId()))
                .get()
                .satisfies(rolledBackOrder -> {
                    assertThat(rolledBackOrder.getStatus())
                            .isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);
                    assertThat(rolledBackOrder.getUpdatedAt()).isEqualTo(createdAt);
                });
    }
}
