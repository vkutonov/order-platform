package com.valentin.paymentservice;

import com.valentin.paymentservice.api.dto.MockPaymentRequest;
import com.valentin.paymentservice.db.OutboxEventRepository;
import com.valentin.paymentservice.db.PaymentRepository;
import com.valentin.paymentservice.domain.dictionary.OutboxEventStatus;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.messaging.event.PaymentFailed;
import com.valentin.paymentservice.messaging.event.PaymentSessionCreated;
import com.valentin.paymentservice.messaging.event.PaymentSucceeded;
import com.valentin.paymentservice.messaging.parser.PaymentMessageParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.outbox.polling.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class MockPaymentScenariosIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("payment_mock_test_db");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private PaymentMessageParser parser;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private OutboxEventRepository outbox;
    @MockitoBean
    private Clock clock;

    @BeforeEach
    void prepare() {
        reset(outbox);
        when(clock.instant()).thenReturn(NOW);
        jdbc.execute("TRUNCATE refunds, payments, processed_events, outbox_events");
    }

    @Test
    void localCheckout_createsSessionAndConfirmsOnceEvenWithConcurrentClicks() throws Exception {
        var request = request(UUID.randomUUID(), 1);
        create(request);
        create(request);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        var session = (PaymentSessionCreated) parser.parse(outbox.findAll().getFirst().getPayload());
        assertThat(session.checkoutUrl()).isEqualTo("http://localhost:8083/mock-payments/" + request.paymentId());
        assertThat(session.providerPaymentId()).isEqualTo("mock-" + request.paymentId());
        mvc.perform(get("/mock-payments/" + request.paymentId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Оплатить")));

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> payAfterStart(request.paymentId(), start));
            var second = executor.submit(() -> payAfterStart(request.paymentId(), start));
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(303);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(303);
        }
        mvc.perform(post("/mock-payments/" + request.paymentId() + "/decline"))
                .andExpect(status().isSeeOther());
        mvc.perform(get("/api/mock/payments/" + request.paymentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/mock-payments/" + request.paymentId()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<form"))));
        assertThat(outbox.findAll()).hasSize(2).allSatisfy(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
            assertThat(event.getAggregateId()).isEqualTo(request.orderId());
        });
        assertThat(outbox.findAll().stream().filter(e -> e.getEventType().equals(PaymentSucceeded.TYPE)))
                .singleElement().satisfies(event -> {
                    var result = (PaymentSucceeded) parser.parse(event.getPayload());
                    assertThat(result.paymentId()).isEqualTo(request.paymentId());
                    assertThat(result.totalPrice()).isEqualByComparingTo(request.totalPrice());
                });

        mvc.perform(post("/api/mock/payments").contentType("application/json")
                        .content(mapper.writeValueAsString(request(request.orderId(), 2))))
                .andExpect(status().isConflict());
        var changed = new MockPaymentRequest(request.paymentId(), request.orderId(), request.userId(), 1,
                new BigDecimal("200.00"), "RUB", request.expiresAt());
        mvc.perform(post("/api/mock/payments").contentType("application/json").content(mapper.writeValueAsString(changed)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/mock/payments").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/mock-payments/" + UUID.randomUUID())).andExpect(status().isNotFound());
        assertThat(outbox.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void declinedOrExpiredPayment_isFinalAndAllowsAnotherAttempt(boolean expired) throws Exception {
        var request = request(UUID.randomUUID(), 1);
        create(request);
        if (expired) {
            when(clock.instant()).thenReturn(NOW.plusSeconds(301));
        }
        String action = expired ? "pay" : "decline";
        mvc.perform(post("/mock-payments/" + request.paymentId() + "/" + action))
                .andExpect(status().isSeeOther());
        mvc.perform(post("/mock-payments/" + request.paymentId() + "/pay"))
                .andExpect(status().isSeeOther());
        assertThat(payments.findById(request.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        var failureEvent = outbox.findAll().stream().filter(e -> e.getEventType().equals(PaymentFailed.TYPE))
                .findFirst().orElseThrow();
        var failure = (PaymentFailed) parser.parse(failureEvent.getPayload());
        assertThat(failure.failureCode()).isEqualTo(expired ? "SESSION_EXPIRED" : "DECLINED");
        assertThat(outbox.count()).isEqualTo(2);

        create(request);
        when(clock.instant()).thenReturn(NOW);
        create(request(request.orderId(), 2));
        assertThat(payments.count()).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(3);
    }

    @Test
    void outboxFailure_rollsBackBothSessionCreationAndPaymentConfirmation() throws Exception {
        var request = request(UUID.randomUUID(), 1);
        doThrow(new IllegalStateException("Simulated database failure")).when(outbox).save(any());
        assertThatThrownBy(() -> create(request)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(payments.count()).isZero();
        assertThat(outbox.count()).isZero();

        reset(outbox);
        create(request);
        doThrow(new IllegalStateException("Simulated database failure")).when(outbox).save(any());
        assertThatThrownBy(() -> mvc.perform(post("/mock-payments/" + request.paymentId() + "/pay")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(payments.findById(request.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(outbox.count()).isEqualTo(1);
    }

    private MockPaymentRequest request(UUID orderId, int attempt) {
        return new MockPaymentRequest(UUID.randomUUID(), orderId, UUID.randomUUID(), attempt,
                new BigDecimal("150.00"), "RUB", NOW.plusSeconds(300).plusNanos(123456789));
    }

    private void create(MockPaymentRequest request) throws Exception {
        mvc.perform(post("/api/mock/payments").contentType("application/json")
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(request.paymentId().toString()));
    }

    private int payAfterStart(UUID id, CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent confirmation did not start");
        }
        return mvc.perform(post("/mock-payments/" + id + "/pay")).andReturn().getResponse().getStatus();
    }
}
