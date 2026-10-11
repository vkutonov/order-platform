package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OutboxEventRepository;
import com.valentin.orderservice.db.ProcessedEventRepository;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderItemEntity;
import com.valentin.orderservice.domain.OutboxEventEntity;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.domain.dictionary.OutboxEventStatus;
import com.valentin.orderservice.messaging.event.InventoryReservedEvent;
import com.valentin.orderservice.messaging.event.OrderReadyForPaymentEvent;
import com.valentin.orderservice.messaging.outbox.OutboxKafkaPublisher;
import com.valentin.orderservice.messaging.outbox.OutboxEventPoller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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

    @MockitoSpyBean
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxKafkaPublisher publisher;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private OrderHistoryRepository orderHistoryRepository;

    @MockitoBean
    private OutboxEventPoller outboxEventPoller;

    @Test
    void handle_whenInventoryReserved_shouldCreatePaymentEventAndPublishThroughExistingPublisher() throws Exception {
        OrderEntity order = createOrder();
        InventoryReservedEvent event = reservedEvent(order);

        handler.handle(event);

        assertThat(orderRepository.findById(order.getId())).get()
                .extracting(OrderEntity::getStatus).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(processedEventRepository.existsById(event.eventId())).isTrue();

        OutboxEventEntity outboxEvent = paymentOutboxEvent(order.getId());
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("Order");
        assertThat(outboxEvent.getEventType()).isEqualTo(OrderReadyForPaymentEvent.TYPE);

        OrderReadyForPaymentEvent paymentEvent = objectMapper.readValue(
                outboxEvent.getPayload(), OrderReadyForPaymentEvent.class
        );
        assertThat(paymentEvent.eventId()).isEqualTo(outboxEvent.getId());
        assertThat(paymentEvent.eventType()).isEqualTo(OrderReadyForPaymentEvent.TYPE);
        assertThat(paymentEvent.eventVersion()).isEqualTo(1);
        assertThat(paymentEvent.orderId()).isEqualTo(order.getId());
        assertThat(paymentEvent.userId()).isEqualTo(order.getUserId());
        assertThat(paymentEvent.totalPrice()).isEqualByComparingTo("250.50");
        assertThat(paymentEvent.currency()).isEqualTo("RUB");
        assertThat(paymentEvent.context()).containsEntry("source", "order-service");
        assertThat(paymentEvent.occurredAt()).isEqualTo(outboxEvent.getCreatedAt());

        when(kafkaTemplate.send("order.events", order.getId().toString(), outboxEvent.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher.publish(outboxEvent);
        verify(kafkaTemplate).send("order.events", order.getId().toString(), outboxEvent.getPayload());

        handler.handle(event);
        assertThat(paymentOutboxEvent(order.getId()).getId()).isEqualTo(outboxEvent.getId());
    }

    @Test
    void handle_whenPaymentOutboxSaveFails_shouldRollbackOrderAndProcessedEvent() {
        OrderEntity order = createOrder();
        InventoryReservedEvent event = reservedEvent(order);
        doThrow(new RuntimeException("Outbox save failed"))
                .when(outboxEventRepository).save(any(OutboxEventEntity.class));

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(RuntimeException.class).hasMessage("Outbox save failed");

        assertThat(processedEventRepository.existsById(event.eventId())).isFalse();
        assertThat(orderRepository.findById(order.getId())).get().satisfies(rolledBackOrder -> {
            assertThat(rolledBackOrder.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);
            assertThat(rolledBackOrder.getUpdatedAt()).isEqualTo(order.getCreatedAt());
        });
        assertThat(outboxEventRepository.findAll())
                .noneMatch(outbox -> outbox.getAggregateId().equals(order.getId()));
    }

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

    private OrderEntity createOrder() {
        OrderEntity order = OrderEntity.createOrderEntity(
                UUID.randomUUID(), new ArrayList<>(), OrderStatus.WAITING_FOR_INVENTORY,
                "RUB", OCCURRED_AT.minusSeconds(60)
        );
        order.addItem(OrderItemEntity.create(
                UUID.randomUUID(), "Keyboard", new BigDecimal("125.25"), "RUB", 2
        ));
        return orderRepository.saveAndFlush(order);
    }

    private InventoryReservedEvent reservedEvent(OrderEntity order) {
        return new InventoryReservedEvent(
                UUID.randomUUID(), "InventoryReservedEvent", 1, order.getId(), RESERVATION_ID,
                Map.of("source", "inventory-service"), OCCURRED_AT
        );
    }

    private OutboxEventEntity paymentOutboxEvent(UUID orderId) {
        var events = outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(orderId)).toList();
        assertThat(events).hasSize(1);
        return events.getFirst();
    }
}
