package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.PaymentResultRepository;
import com.valentin.orderservice.db.ProcessedEventRepository;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderItemEntity;
import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.domain.dictionary.PaymentResultStatus;
import com.valentin.orderservice.messaging.consumer.PaymentEventListener;
import com.valentin.orderservice.messaging.outbox.OutboxEventPoller;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = {
        "order.events", "inventory.events", "payment.events",
        "inventory.events.order-service.dlt", "payment.events.order-service.dlt"
}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Testcontainers
@DirtiesContext
class PaymentScenariosIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final String PAYMENTS = "payment.events";
    private static final String DLT = "payment.events.order-service.dlt";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4-bookworm");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private EmbeddedKafkaBroker broker;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Autowired private PaymentEventListener listener;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderRepository orders;
    @Autowired private PaymentResultRepository results;
    @Autowired private ProcessedEventRepository inbox;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @MockitoSpyBean private PaymentEventHandler handler;
    @MockitoSpyBean private OrderHistoryRepository history;
    @MockitoBean private OutboxEventPoller poller;
    @MockitoBean private Clock clock;

    @BeforeEach
    void prepare() {
        reset(history, handler);
        when(clock.instant()).thenReturn(NOW);
        ContainerTestUtils.waitForAssignment(listeners.getListenerContainer("paymentResults"), 3);
        jdbc.execute("TRUNCATE order_payment_results, order_status_history, order_items, orders, processed_events, outbox_events");
    }

    @Test
    void successUpdatesOrderAndHistoryOnce_andLateFailureCannotUndoPayment() throws Exception {
        var order = order(OrderStatus.WAITING_FOR_PAYMENT);
        var event = event(order, "PaymentSucceeded");
        send(event);
        send(event);
        event.put("eventId", UUID.randomUUID());
        send(event);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(inbox.count()).isEqualTo(2);
            assertThat(orderStatus(order)).isEqualTo(OrderStatus.PAID);
        });
        assertThat(results.count()).isEqualTo(1);
        assertThat(history.findAll()).singleElement().satisfies(change -> {
            assertThat(change.getOldStatus()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
            assertThat(change.getNewStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(change.getReason()).isEqualTo(OrderChangeHistoryReason.PAYMENT_SUCCEEDED);
        });
        mvc.perform(get("/api/orders/" + order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get("/api/orders/" + order.getId() + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("PAYMENT_SUCCEEDED"));

        send(event(order, "PaymentFailed"));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inbox.count()).isEqualTo(3));
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.PAID);
        assertThat(history.count()).isEqualTo(1);
        assertThat(results.count()).isEqualTo(2);

        mvc.perform(post("/api/orders/" + order.getId() + "/payment-success"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + order.getId() + "/payment-failed"))
                .andExpect(status().isNotFound());
    }

    @Test
    void failedAttemptKeepsOrderWaiting_andConflictingFinalResultReachesDlt() throws Exception {
        var order = order(OrderStatus.WAITING_FOR_PAYMENT);
        var failure = event(order, "PaymentFailed");
        send(failure);
        failure.put("eventId", UUID.randomUUID());
        send(failure);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inbox.count()).isEqualTo(2));
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(results.findAll()).singleElement().satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(PaymentResultStatus.FAILED);
            assertThat(result.getFailureCode()).isEqualTo("SESSION_EXPIRED");
        });
        assertThat(history.findAll()).singleElement().satisfies(change -> {
            assertThat(change.getOldStatus()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
            assertThat(change.getNewStatus()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
            assertThat(change.getReason()).isEqualTo(OrderChangeHistoryReason.PAYMENT_FAILED);
        });

        var conflict = event(order, "PaymentSucceeded");
        conflict.put("paymentId", failure.get("paymentId"));
        try (var rejected = consumer(DLT)) {
            send(conflict);
            assertThat(receive(rejected, order.getId().toString()).value())
                    .isEqualTo(mapper.writeValueAsString(conflict));
        }
        assertThat(inbox.existsById((UUID) conflict.get("eventId"))).isFalse();
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(history.count()).isEqualTo(1);
        assertThat(results.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "null", "version", "paymentId", "amount", "currency", "key", "orderId"})
    void invalidResultReachesPaymentDltOnOriginalPartition_withoutChangingOrder(String invalidField) throws Exception {
        var order = order(OrderStatus.WAITING_FOR_PAYMENT);
        var event = event(order, "PaymentSucceeded");
        switch (invalidField) {
            case "version" -> event.put("eventVersion", 2);
            case "paymentId" -> event.remove("paymentId");
            case "amount" -> event.put("totalPrice", new BigDecimal("151.00"));
            case "currency" -> event.put("currency", "USD");
            case "orderId" -> event.put("orderId", UUID.randomUUID());
        }
        String payload = switch (invalidField) {
            case "json" -> "invalid json";
            case "null" -> "null";
            default -> mapper.writeValueAsString(event);
        };
        String key = invalidField.equals("key") ? UUID.randomUUID().toString() : event.get("orderId").toString();
        try (var rejected = consumer(DLT)) {
            kafka.send(new ProducerRecord<>(PAYMENTS, 2, key, payload)).get(10, TimeUnit.SECONDS);
            var record = receive(rejected, key);
            assertThat(record.partition()).isEqualTo(2);
            assertThat(record.value()).isEqualTo(payload);
        }
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(inbox.count()).isZero();
        assertThat(results.count()).isZero();
        assertThat(history.count()).isZero();
    }

    @Test
    void databaseFailureRollsBackInboxResultOrderAndHistory_andKafkaRetryCanRecover() throws Exception {
        var order = order(OrderStatus.WAITING_FOR_PAYMENT);
        var event = event(order, "PaymentSucceeded");
        var record = new ConsumerRecord<>(PAYMENTS, 0, 0, order.getId().toString(), mapper.writeValueAsString(event));
        doThrow(new IllegalStateException("Simulated database failure")).when(history).save(any());

        assertThatThrownBy(() -> listener.consume(record)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(inbox.count()).isZero();
        assertThat(results.count()).isZero();
        assertThat(history.count()).isZero();

        reset(history);
        reset(handler);
        doThrow(new DataAccessResourceFailureException("Simulated temporary failure"))
                .doCallRealMethod().when(handler).handle(any());
        send(event);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(orderStatus(order)).isEqualTo(OrderStatus.PAID));
        assertThat(inbox.count()).isEqualTo(1);
        assertThat(results.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
        verify(handler, times(2)).handle(any());
        verify(history).save(any());
    }

    @Test
    void concurrentDeliveriesWithDifferentEventIdsApplyOnePaymentResult() throws Exception {
        var order = order(OrderStatus.WAITING_FOR_PAYMENT);
        var first = event(order, "PaymentSucceeded");
        var second = new HashMap<>(first);
        second.put("eventId", UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                consumeAfterStart(first, start);
                return null;
            });
            var two = executor.submit(() -> {
                consumeAfterStart(second, start);
                return null;
            });
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        }
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.PAID);
        assertThat(inbox.count()).isEqualTo(2);
        assertThat(results.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
    }

    @Test
    void lateSuccessForCancelledOrderIsSaved_withoutReopeningOrder_andInventoryUsesItsOwnDlt() throws Exception {
        var order = order(OrderStatus.CANCELLED);
        send(event(order, "PaymentSucceeded"));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inbox.count()).isEqualTo(1));
        assertThat(orderStatus(order)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(results.findAll()).singleElement()
                .extracting(result -> result.getStatus()).isEqualTo(PaymentResultStatus.SUCCEEDED);
        assertThat(history.count()).isZero();

        try (var rejected = consumer("inventory.events.order-service.dlt")) {
            String key = UUID.randomUUID().toString();
            kafka.send("inventory.events", key, "invalid inventory json").get(10, TimeUnit.SECONDS);
            assertThat(receive(rejected, key).value()).isEqualTo("invalid inventory json");
        }
    }

    private OrderEntity order(OrderStatus status) {
        var order = OrderEntity.createOrderEntity(UUID.randomUUID(), new ArrayList<>(), status, "RUB", NOW.minusSeconds(60));
        order.addItem(OrderItemEntity.create(UUID.randomUUID(), "Keyboard", new BigDecimal("150.00"), "RUB", 1));
        return orders.saveAndFlush(order);
    }

    private OrderStatus orderStatus(OrderEntity order) {
        return orders.findById(order.getId()).orElseThrow().getStatus();
    }

    private Map<String, Object> event(OrderEntity order, String type) {
        var event = new HashMap<String, Object>();
        event.put("eventId", UUID.randomUUID());
        event.put("eventType", type);
        event.put("eventVersion", 1);
        event.put("orderId", order.getId());
        event.put("paymentId", UUID.randomUUID());
        event.put("totalPrice", order.getTotalPrice());
        event.put("currency", order.getCurrency());
        event.put("context", Map.of("source", "payment-service"));
        event.put("occurredAt", NOW);
        if (type.equals("PaymentFailed")) {
            event.put("failureCode", "SESSION_EXPIRED");
        }
        return event;
    }

    private void send(Map<String, Object> event) throws Exception {
        kafka.send(PAYMENTS, event.get("orderId").toString(), mapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
    }

    private void consumeAfterStart(Map<String, Object> event, CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent delivery did not start");
        }
        listener.consume(new ConsumerRecord<>(PAYMENTS, 0, 0, event.get("orderId").toString(), mapper.writeValueAsString(event)));
    }

    private KafkaConsumer<String, String> consumer(String topic) {
        var consumer = new KafkaConsumer<>(KafkaTestUtils.consumerProps(broker, UUID.randomUUID().toString(), false),
                new StringDeserializer(), new StringDeserializer());
        broker.consumeFromAnEmbeddedTopic(consumer, true, topic);
        return consumer;
    }

    private ConsumerRecord<String, String> receive(KafkaConsumer<String, String> consumer, String key) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            for (var record : consumer.poll(Duration.ofMillis(100))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Kafka record did not arrive: " + key);
    }
}
