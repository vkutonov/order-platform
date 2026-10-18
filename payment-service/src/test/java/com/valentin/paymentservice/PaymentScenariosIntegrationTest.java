package com.valentin.paymentservice;

import com.valentin.paymentservice.db.OutboxEventRepository;
import com.valentin.paymentservice.db.PaymentRepository;
import com.valentin.paymentservice.domain.dictionary.OutboxEventStatus;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.logic.OrderReadyForPaymentEventHandler;
import com.valentin.paymentservice.messaging.event.OrderReadyForPaymentEvent;
import com.valentin.paymentservice.messaging.event.PaymentFailed;
import com.valentin.paymentservice.messaging.event.PaymentSucceeded;
import com.valentin.paymentservice.messaging.parser.PaymentMessageParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.outbox.polling.enabled=false", "spring.kafka.listener.auto-startup=false"})
@AutoConfigureMockMvc
@Testcontainers
class PaymentScenariosIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4-bookworm");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private OrderReadyForPaymentEventHandler orders;
    @Autowired private PaymentMessageParser parser;
    @Autowired private PaymentRepository payments;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private OutboxEventRepository outbox;
    @MockitoBean private Clock clock;

    @BeforeEach
    void prepare() {
        reset(outbox);
        when(clock.instant()).thenReturn(NOW);
        jdbc.execute("TRUNCATE refunds, payments, processed_events, outbox_events");
    }

    @Test
    void paymentsAreListed_andConcurrentConfirmationsPublishOneResult() throws Exception {
        var id = createPayment();
        createPayment();
        mvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> confirmAfterStart(id, start));
            var second = executor.submit(() -> confirmAfterStart(id, start));
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        mvc.perform(post("/api/payments/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
            assertThat(event.getAggregateId()).isEqualTo(payments.findById(id).orElseThrow().getOrderId());
            var result = (PaymentSucceeded) parser.parse(event.getPayload());
            assertThat(result.paymentId()).isEqualTo(id);
            assertThat(result.totalPrice()).isEqualByComparingTo("150.00");
        });
        mvc.perform(post("/api/payments/" + UUID.randomUUID() + "/confirm"))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredPaymentCannotBeConfirmed_andFailureIsPublishedOnce() throws Exception {
        var id = createPayment();
        when(clock.instant()).thenReturn(NOW.plusSeconds(901));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/payments/" + id + "/confirm"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.failureCode").value("SESSION_EXPIRED"));
        }
        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            var failure = (PaymentFailed) parser.parse(event.getPayload());
            assertThat(failure.paymentId()).isEqualTo(id);
            assertThat(failure.failureCode()).isEqualTo("SESSION_EXPIRED");
        });
    }

    @Test
    void outboxFailureRollsBackConfirmation_andRequestCanBeRetried() throws Exception {
        var id = createPayment();
        doThrow(new IllegalStateException("Simulated database failure")).when(outbox).save(any());
        mvc.perform(post("/api/payments/" + id + "/confirm"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Internal server error"));
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(outbox.count()).isZero();

        reset(outbox);
        mvc.perform(post("/api/payments/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(outbox.count()).isEqualTo(1);
    }

    private UUID createPayment() {
        var event = new OrderReadyForPaymentEvent(UUID.randomUUID(), OrderReadyForPaymentEvent.TYPE, 1,
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"), "RUB", Map.of(), NOW);
        orders.handle(event);
        return payments.findByOrderIdAndAttemptNumber(event.orderId(), 1).orElseThrow().getId();
    }

    private int confirmAfterStart(UUID id, CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent confirmation did not start");
        }
        return mvc.perform(post("/api/payments/" + id + "/confirm")).andReturn().getResponse().getStatus();
    }
}
