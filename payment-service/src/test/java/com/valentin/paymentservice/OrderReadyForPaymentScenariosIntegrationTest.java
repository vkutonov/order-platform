package com.valentin.paymentservice;

import com.valentin.paymentservice.db.OutboxEventRepository;
import com.valentin.paymentservice.db.PaymentRepository;
import com.valentin.paymentservice.db.ProcessedEventRepository;
import com.valentin.paymentservice.domain.dictionary.OutboxEventStatus;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.messaging.consumer.OrderReadyForPaymentEventListener;
import com.valentin.paymentservice.messaging.event.OrderReadyForPaymentEvent;
import com.valentin.paymentservice.messaging.event.PaymentFailed;
import com.valentin.paymentservice.messaging.event.PaymentSucceeded;
import com.valentin.paymentservice.messaging.event.OrderLifecycleEvent;
import com.valentin.paymentservice.messaging.outbox.OutboxEventPoller;
import com.valentin.paymentservice.messaging.parser.PaymentMessageParser;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.outbox.polling.enabled=false", "app.payment.expiration.enabled=false"})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"order.events", "payment.events", "order.events.payment-service.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Testcontainers
@DirtiesContext
class OrderReadyForPaymentScenariosIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    private static final String ORDERS = "order.events";
    private static final String EVENTS = "payment.events";
    private static final String DLT = "order.events.payment-service.dlt";

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
    @Autowired private OrderReadyForPaymentEventListener listener;
    @Autowired private ObjectMapper mapper;
    @Autowired private PaymentMessageParser parser;
    @MockitoSpyBean private PaymentRepository payments;
    @Autowired private ProcessedEventRepository inbox;
    @Autowired private OutboxEventPoller poller;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private OutboxEventRepository outbox;
    @MockitoBean private Clock clock;

    @BeforeEach
    void prepare() {
        reset(payments);
        when(clock.instant()).thenReturn(NOW);
        ContainerTestUtils.waitForAssignment(listeners.getListenerContainer("orderReadyForPayment"), 1);
        jdbc.execute("TRUNCATE refunds, payments, processed_events, outbox_events");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationBeforeOrAfterReadinessPreventsConfirmation_andPublishesOneFailure(boolean readyFirst) throws Exception {
        var ready = new OrderReadyForPaymentEvent(UUID.randomUUID(), OrderReadyForPaymentEvent.TYPE, 1,
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"), "RUB", Map.of(), NOW);
        var cancelled = new OrderLifecycleEvent(UUID.randomUUID(), OrderLifecycleEvent.CANCELLED, 1, ready.orderId(), NOW);
        if (readyFirst) {
            kafka.send(ORDERS, ready.orderId().toString(), mapper.writeValueAsString(ready)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(payments.count()).isEqualTo(1));
        }
        kafka.send(ORDERS, ready.orderId().toString(), mapper.writeValueAsString(cancelled)).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inbox.existsById(cancelled.eventId())).isTrue());
        if (!readyFirst) {
            kafka.send(ORDERS, ready.orderId().toString(), mapper.writeValueAsString(ready)).get(10, TimeUnit.SECONDS);
        }
        kafka.send(ORDERS, ready.orderId().toString(), mapper.writeValueAsString(cancelled)).get(10, TimeUnit.SECONDS);
        var duplicate = new OrderLifecycleEvent(UUID.randomUUID(), OrderLifecycleEvent.CANCELLED, 1, ready.orderId(), NOW);
        kafka.send(ORDERS, ready.orderId().toString(), mapper.writeValueAsString(duplicate)).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(inbox.count()).isEqualTo(3);
            assertThat(payments.findByOrderIdAndAttemptNumber(ready.orderId(), 1).orElseThrow().getFailureCode())
                    .isEqualTo("ORDER_CANCELLED");
        });
        mvc.perform(post("/api/payments/by-order/" + ready.orderId() + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("ORDER_CANCELLED"));
        assertThat(outbox.findAll()).singleElement()
                .satisfies(event -> assertThat(parser.parse(event.getPayload())).isInstanceOf(PaymentFailed.class));
    }

    @Test
    void orderReadinessCreatesOnePayment_andConfirmationPublishesResult_whileInvalidEventsReachDlt() throws Exception {
        var event = event(NOW);
        try (var results = consumer(EVENTS); var rejected = consumer(DLT)) {
            kafka.send(ORDERS, event.orderId().toString(),
                    mapper.writeValueAsString(Map.of("eventType", "OrderCreatedEvent", "orderId", event.orderId())))
                    .get(10, TimeUnit.SECONDS);
            send(event);
            send(event);
            send(new OrderReadyForPaymentEvent(UUID.randomUUID(), OrderReadyForPaymentEvent.TYPE, 1,
                    event.orderId(), event.userId(), event.totalPrice(), event.currency(),
                    event.context(), NOW.plusSeconds(1)));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(inbox.count()).isEqualTo(2);
                assertThat(payments.count()).isEqualTo(1);
                assertThat(outbox.count()).isZero();
            });
            var payment = payments.findByOrderIdAndAttemptNumber(event.orderId(), 1).orElseThrow();
            assertThat(payment.getId()).isNotEqualTo(event.eventId());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            mvc.perform(get("/api/payments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].paymentId").value(payment.getId().toString()));

            mvc.perform(post("/api/payments/by-order/" + payment.getOrderId() + "/confirm"))
                    .andExpect(status().isOk());
            poller.pollAndPublish();
            var success = (PaymentSucceeded) parser.parse(receive(results, event.orderId().toString()).value());
            assertThat(success.paymentId()).isEqualTo(payment.getId());
            assertThat(success.eventId()).isNotEqualTo(payment.getId()).isNotEqualTo(event.eventId());
            assertThat(outbox.findAll()).hasSize(1).allSatisfy(outboxEvent ->
                    assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED));

            var conflict = new OrderReadyForPaymentEvent(UUID.randomUUID(), OrderReadyForPaymentEvent.TYPE, 1,
                    event.orderId(), event.userId(), new BigDecimal("200.00"), "RUB", event.context(), NOW);
            send(conflict);
            assertThat(receive(rejected, event.orderId().toString()).value())
                    .isEqualTo(mapper.writeValueAsString(conflict));
            String invalidKey = UUID.randomUUID().toString();
            kafka.send(ORDERS, invalidKey, "invalid json").get(10, TimeUnit.SECONDS);
            assertThat(receive(rejected, invalidKey).value()).isEqualTo("invalid json");
            assertThat(inbox.existsById(conflict.eventId())).isFalse();
            assertThat(inbox.count()).isEqualTo(2);
            assertThat(payments.count()).isEqualTo(1);
            assertThat(outbox.count()).isEqualTo(1);
        }
    }

    @Test
    void delayedOrderReadinessCreatesFailedPayment() throws Exception {
        var event = event(NOW.minusSeconds(16 * 60));
        send(event);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(inbox.existsById(event.eventId())).isTrue();
            assertThat(outbox.count()).isEqualTo(1);
        });
        var payment = payments.findByOrderIdAndAttemptNumber(event.orderId(), 1).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("SESSION_EXPIRED");
        var failure = (PaymentFailed) parser.parse(outbox.findAll().getFirst().getPayload());
        assertThat(failure.paymentId()).isEqualTo(payment.getId()).isNotEqualTo(event.eventId());
        assertThat(failure.failureCode()).isEqualTo("SESSION_EXPIRED");
    }

    @Test
    void databaseFailureRollsBackInboxPaymentAndOutbox_andRedeliveryCanProcessEvent() throws Exception {
        var event = event(NOW.minusSeconds(16 * 60));
        doThrow(new IllegalStateException("Simulated database failure"))
                .when(payments).saveAndFlush(any());
        var record = new ConsumerRecord<>(ORDERS, 0, 0, event.orderId().toString(), mapper.writeValueAsString(event));
        assertThatThrownBy(() -> listener.consume(record)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(inbox.count()).isZero();
        assertThat(payments.count()).isZero();
        assertThat(outbox.count()).isZero();

        reset(payments);
        send(event);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(inbox.existsById(event.eventId())).isTrue();
            assertThat(payments.count()).isEqualTo(1);
            assertThat(outbox.count()).isEqualTo(1);
        });
    }

    private OrderReadyForPaymentEvent event(Instant occurredAt) {
        return new OrderReadyForPaymentEvent(UUID.randomUUID(), OrderReadyForPaymentEvent.TYPE, 1,
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"), "RUB",
                Map.of("source", "order-service"), occurredAt);
    }

    private void send(OrderReadyForPaymentEvent event) throws Exception {
        kafka.send(ORDERS, event.orderId().toString(), mapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
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
