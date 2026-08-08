package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.InventoryItemRepository;
import com.valentin.inventoryservice.db.OutboxEventRepository;
import com.valentin.inventoryservice.db.ProcessedEventsRepository;
import com.valentin.inventoryservice.db.ProductRepository;
import com.valentin.inventoryservice.db.ReservationRepository;
import com.valentin.inventoryservice.domain.InventoryItemEntity;
import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import com.valentin.inventoryservice.exception.ProductNotFoundException;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "server.port=0")
@Testcontainers
class OrderCreatedEventHandlerIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:18.4-bookworm");

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MISSING_PRODUCT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("inventory_test_db")
            .withUsername("inventory_test_user")
            .withPassword("inventory_test_password");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderCreatedEventHandler handler;

    @Autowired
    private ProcessedEventsRepository processedEventsRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @MockitoBean
    private OutboxEventRepository outboxEventRepository;

    @Test
    void handle_whenReservationFails_shouldRollbackProcessedEvent() {
        OrderCreatedEvent event = event(MISSING_PRODUCT_ID);

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(ProductNotFoundException.class);

        assertThat(processedEventsRepository.existsById(EVENT_ID)).isFalse();
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void handle_whenOutboxSaveFails_shouldRollbackWholeTransaction() {
        ProductEntity product = productRepository.saveAndFlush(ProductEntity.create(
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("5000.00"),
                "RUB",
                ProductStatus.ACTIVE,
                Instant.parse("2026-08-05T09:00:00Z")
        ));

        inventoryItemRepository.saveAndFlush(InventoryItemEntity.create(
                product.getId(),
                10,
                Instant.parse("2026-08-05T09:00:00Z")
        ));

        OrderCreatedEvent event = event(product.getId());

        when(outboxEventRepository.save(any()))
                .thenThrow(new RuntimeException("Outbox save failed"));

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Outbox save failed");

        assertThat(processedEventsRepository.existsById(EVENT_ID)).isFalse();
        assertThat(reservationRepository.findByOrderId(ORDER_ID)).isEmpty();
        assertThat(inventoryItemRepository.findByProductId(product.getId()))
                .get()
                .satisfies(inventoryItem -> {
                    assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(10);
                    assertThat(inventoryItem.getReservedQuantity()).isZero();
                });
        verify(outboxEventRepository).save(any());
    }

    private OrderCreatedEvent event(UUID productId) {
        return new OrderCreatedEvent(
                EVENT_ID,
                "OrderCreatedEvent",
                1,
                ORDER_ID,
                USER_ID,
                List.of(new OrderCreatedItem(
                        productId,
                        "Keyboard",
                        new BigDecimal("5000.00"),
                        "RUB",
                        1
                )),
                Map.of("source", "order-service"),
                Instant.parse("2026-08-05T10:00:00Z")
        );
    }
}
