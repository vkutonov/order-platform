package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.ProcessedEventsRepository;
import com.valentin.inventoryservice.db.ReservationRepository;
import com.valentin.inventoryservice.exception.ProductNotFoundException;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    @Test
    void handle_whenReservationFails_shouldRollbackProcessedEvent() {
        OrderCreatedEvent event = event();

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(ProductNotFoundException.class);

        assertThat(processedEventsRepository.existsById(EVENT_ID)).isFalse();
        assertThat(reservationRepository.count()).isZero();
    }

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent(
                EVENT_ID,
                "OrderCreatedEvent",
                1,
                ORDER_ID,
                USER_ID,
                List.of(new OrderCreatedItem(
                        MISSING_PRODUCT_ID,
                        "Missing product",
                        new BigDecimal("5000.00"),
                        "RUB",
                        1
                )),
                Map.of("source", "order-service"),
                Instant.parse("2026-08-05T10:00:00Z")
        );
    }
}
