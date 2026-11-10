package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.InventoryItemRepository;
import com.valentin.inventoryservice.db.ProcessedEventsRepository;
import com.valentin.inventoryservice.db.ProductRepository;
import com.valentin.inventoryservice.db.ReservationRepository;
import com.valentin.inventoryservice.domain.InventoryItemEntity;
import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;
import com.valentin.inventoryservice.messaging.consumer.OrderCreatedEventListener;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedItem;
import com.valentin.inventoryservice.messaging.event.OrderLifecycleEvent;
import com.valentin.inventoryservice.messaging.outbox.OutboxEventPoller;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.grpc.server.port=0")
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"order.events", "inventory.events", "order.events.inventory-service.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Testcontainers
@DirtiesContext
class OrderLifecycleScenariosIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4-bookworm");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderCreatedEventListener listener;
    @Autowired private ProductRepository products;
    @Autowired private InventoryItemRepository stock;
    @Autowired private ReservationRepository reservations;
    @Autowired private ProcessedEventsRepository inbox;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @MockitoSpyBean private ReservationService service;
    @MockitoBean private OutboxEventPoller poller;
    private UUID productId;

    @BeforeEach
    void prepare() {
        reset(service);
        jdbc.execute("TRUNCATE reservation_items, reservations, inventory_items, products, processed_events, outbox_events CASCADE");
        var product = products.saveAndFlush(ProductEntity.create("Keyboard", "Keyboard", new BigDecimal("150.00"),
                "RUB", ProductStatus.ACTIVE, NOW));
        productId = product.getId();
        stock.saveAndFlush(InventoryItemEntity.create(productId, 10, NOW));
    }

    @Test
    void deletingProductHidesIt_andPreservesExistingReservationWhileRejectingNewOnes() throws Exception {
        var created = created();
        consume(created, created.orderId());
        mvc.perform(delete("/api/products/" + productId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/products/" + productId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/products/" + productId + "/stock")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/" + UUID.randomUUID())).andExpect(status().isNotFound());
        assertThat(products.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.DELETED);
        assertStock(10, 2);

        send(lifecycle(created.orderId(), OrderLifecycleEvent.PAID));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertStock(8, 0));
        assertThat(reservations.findByOrderId(created.orderId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.COMMITTED);

        var laterOrder = created();
        consume(laterOrder, laterOrder.orderId());
        assertThat(reservations.findByOrderId(laterOrder.orderId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertStock(8, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {OrderLifecycleEvent.PAID, OrderLifecycleEvent.CANCELLED})
    void orderResultChangesStockOnce_evenWithDifferentDuplicateEventIds(String type) throws Exception {
        var created = created();
        consume(created, created.orderId());
        var event = lifecycle(created.orderId(), type);
        send(event);
        send(event);
        send(lifecycle(created.orderId(), type));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(inbox.count()).isEqualTo(3);
            assertThat(reservations.findByOrderId(created.orderId()).orElseThrow().getStatus())
                    .isEqualTo(type.equals(OrderLifecycleEvent.PAID) ? ReservationStatus.COMMITTED : ReservationStatus.RELEASED);
        });
        consume(new OrderCreatedEvent(UUID.randomUUID(), created.eventType(), 1, created.orderId(),
                created.userId(), created.items(), created.context(), NOW), created.orderId());
        assertStock(type.equals(OrderLifecycleEvent.PAID) ? 8 : 10, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationBeforeOrDuringCreationCannotLeaveReservedStock(boolean concurrent) throws Exception {
        var created = created();
        var cancelled = lifecycle(created.orderId(), OrderLifecycleEvent.CANCELLED);
        if (concurrent) {
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var creation = executor.submit(() -> {
                    start.await();
                    consume(created, created.orderId());
                    return null;
                });
                var cancellation = executor.submit(() -> {
                    start.await();
                    consume(cancelled, created.orderId());
                    return null;
                });
                start.countDown();
                creation.get(10, TimeUnit.SECONDS);
                cancellation.get(10, TimeUnit.SECONDS);
            }
        } else {
            consume(cancelled, created.orderId());
            consume(created, created.orderId());
        }
        assertThat(inbox.count()).isEqualTo(2);
        reservations.findByOrderId(created.orderId()).ifPresent(reservation ->
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED));
        assertStock(10, 0);
    }

    @Test
    void failedReleaseRollsBackInboxAndStock_andKafkaRetryCompletesIt() throws Exception {
        var created = created();
        consume(created, created.orderId());
        var cancelled = lifecycle(created.orderId(), OrderLifecycleEvent.CANCELLED);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Simulated release failure");
        }).when(service).release(created.orderId());
        assertThatThrownBy(() -> consume(cancelled, created.orderId())).isInstanceOf(IllegalStateException.class);
        assertThat(inbox.existsById(cancelled.eventId())).isFalse();
        assertThat(reservations.findByOrderId(created.orderId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertStock(10, 2);

        reset(service);
        send(cancelled);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertStock(10, 0));
        assertThat(inbox.existsById(cancelled.eventId())).isTrue();
    }

    private void assertStock(int quantity, int reserved) {
        var item = stock.findByProductId(productId).orElseThrow();
        assertThat(item.getQuantityOnHand()).isEqualTo(quantity);
        assertThat(item.getReservedQuantity()).isEqualTo(reserved);
    }

    private OrderCreatedEvent created() {
        return new OrderCreatedEvent(UUID.randomUUID(), "OrderCreatedEvent", 1, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderCreatedItem(productId, "Keyboard", new BigDecimal("150.00"), "RUB", 2)), Map.of(), NOW);
    }

    private OrderLifecycleEvent lifecycle(UUID orderId, String type) {
        return new OrderLifecycleEvent(UUID.randomUUID(), type, 1, orderId, NOW);
    }

    private void consume(Object event, UUID orderId) {
        listener.consume(new ConsumerRecord<>("order.events", 0, 0, orderId.toString(), mapper.writeValueAsString(event)));
    }

    private void send(OrderLifecycleEvent event) throws Exception {
        kafka.send("order.events", event.orderId().toString(), mapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
    }
}
