package com.valentin.paymentservice.db;

import com.valentin.paymentservice.domain.OutboxEventEntity;
import com.valentin.paymentservice.domain.PaymentEntity;
import com.valentin.paymentservice.domain.RefundEntity;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.domain.dictionary.OutboxEventStatus;
import com.valentin.paymentservice.domain.dictionary.RefundStatus;
import com.valentin.paymentservice.exception.InvalidPaymentStatusException;
import com.valentin.paymentservice.exception.InvalidRefundStatusException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4-bookworm");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentRepository payments;
    @Autowired
    private RefundRepository refunds;
    @Autowired
    private ProcessedEventRepository inbox;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    @Test
    void failedAttempts_canBeRetriedButSuccessfulChargeAndRefundRemainFinal() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity first = payment(orderId, 1);
        first.markFailed("SESSION_REJECTED", NOW);
        first.markFailed("SESSION_REJECTED", NOW.plusSeconds(1));
        assertThat(first.getUpdatedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> first.markSucceeded(NOW))
                .isInstanceOf(InvalidPaymentStatusException.class);
        payments.saveAndFlush(first);

        PaymentEntity second = payment(orderId, 2);
        second.markSessionCreated("session-" + second.getId(), "http://provider/pay/2", NOW);
        second.markFailed("DECLINED", NOW);
        second.markFailed("DECLINED", NOW.plusSeconds(1));
        assertThat(second.getUpdatedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> second.markSessionCreated("replacement", "http://provider/pay/other", NOW))
                .isInstanceOf(InvalidPaymentStatusException.class);
        payments.saveAndFlush(second);

        PaymentEntity third = payment(orderId, 3);
        assertThatThrownBy(() -> third.markSucceeded(NOW))
                .isInstanceOf(InvalidPaymentStatusException.class);
        String sessionId = "session-" + third.getId();
        third.markSessionCreated(sessionId, "http://provider/pay/3", NOW);
        third.markSessionCreated(sessionId, "http://provider/pay/3", NOW.plusSeconds(1));
        assertThat(third.getUpdatedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> third.markSessionCreated("replacement", "http://provider/pay/other", NOW))
                .isInstanceOf(InvalidPaymentStatusException.class);
        third.markSucceeded(NOW.plusSeconds(2));
        third.markSucceeded(NOW.plusSeconds(3));
        assertThat(third.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThatThrownBy(() -> third.markFailed("DECLINED", NOW.plusSeconds(4)))
                .isInstanceOf(InvalidPaymentStatusException.class);
        PaymentEntity saved = payments.saveAndFlush(third);

        Instant refundTime = NOW.plusSeconds(5);
        RefundEntity refund = RefundEntity.create(UUID.randomUUID(), saved, "RESERVATION_EXPIRED", refundTime);
        refund.markSucceeded("refund-" + refund.getId(), refundTime);
        refund.markSucceeded("refund-" + refund.getId(), refundTime.plusSeconds(1));
        assertThat(refund.getUpdatedAt()).isEqualTo(refundTime);
        assertThatThrownBy(() -> refund.markFailed("DECLINED", refundTime))
                .isInstanceOf(InvalidRefundStatusException.class);
        assertThatThrownBy(() -> refund.markSucceeded("another-refund", refundTime))
                .isInstanceOf(InvalidRefundStatusException.class);
        refunds.saveAndFlush(refund);

        assertThat(payments.findByOrderIdAndAttemptNumber(orderId, 1)).get()
                .extracting(PaymentEntity::getStatus).isEqualTo(PaymentStatus.FAILED);
        assertThat(payments.findById(second.getId())).get()
                .extracting(PaymentEntity::getStatus).isEqualTo(PaymentStatus.FAILED);
        assertThat(payments.findById(third.getId())).get()
                .extracting(PaymentEntity::getStatus).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(refunds.findByPaymentId(third.getId())).get()
                .extracting(RefundEntity::getStatus).isEqualTo(RefundStatus.SUCCEEDED);
        assertThatThrownBy(() -> refunds.saveAndFlush(
                RefundEntity.create(UUID.randomUUID(), saved, "RESERVATION_EXPIRED", refundTime)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payments.saveAndFlush(payment(orderId, 4)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeAttempt_isProtectedAgainstConcurrentInserts() throws Exception {
        UUID orderId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> insertAfterStart(payment(orderId, 1), start));
            var second = executor.submit(() -> insertAfterStart(payment(orderId, 2), start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void attemptNumber_andProviderSessionCannotBeReused() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity first = payment(orderId, 1);
        first.markFailed("DECLINED", NOW);
        payments.saveAndFlush(first);

        assertThatThrownBy(() -> payments.saveAndFlush(payment(orderId, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);

        PaymentEntity withSession = payment(UUID.randomUUID(), 1);
        withSession.markSessionCreated("unique-" + first.getId(), "http://provider/pay/1", NOW);
        payments.saveAndFlush(withSession);
        PaymentEntity duplicateSession = payment(UUID.randomUUID(), 1);
        duplicateSession.markSessionCreated(withSession.getProviderPaymentId(), "http://provider/pay/1", NOW);

        assertThatThrownBy(() -> payments.saveAndFlush(duplicateSession))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.findByProviderPaymentId(withSession.getProviderPaymentId())).isPresent();
    }

    @Test
    void version_rejectsStalePaymentUpdate() {
        PaymentEntity saved = payments.saveAndFlush(payment(UUID.randomUUID(), 1));
        PaymentEntity first = payments.findById(saved.getId()).orElseThrow();
        PaymentEntity stale = payments.findById(saved.getId()).orElseThrow();
        first.markSessionCreated("session-" + saved.getId(), "http://provider/pay/1", NOW);
        payments.saveAndFlush(first);
        stale.markFailed("DECLINED", NOW);

        assertThatThrownBy(() -> payments.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(payments.findById(saved.getId())).get()
                .extracting(PaymentEntity::getStatus).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void inboxPaymentAndOutbox_commitTogetherAndDuplicateDoesNotRepeatEffect() {
        PaymentEntity payment = payment(UUID.randomUUID(), 1);
        UUID eventId = UUID.randomUUID();
        OutboxEventEntity event = event(payment.getOrderId());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(inbox.tryInsert(eventId, "CreatePaymentRequested", payment.getOrderId(), NOW)).isEqualTo(1);
            payments.saveAndFlush(payment);
            outbox.saveAndFlush(event);
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(inbox.tryInsert(eventId, "CreatePaymentRequested", payment.getOrderId(), NOW)).isZero());

        assertThat(inbox.existsById(eventId)).isTrue();
        assertThat(payments.existsById(payment.getId())).isTrue();
        assertThat(outbox.existsById(event.getId())).isTrue();
    }

    @Test
    void transactionFailure_rollsBackInboxPaymentAndOutbox() {
        PaymentEntity payment = payment(UUID.randomUUID(), 1);
        UUID eventId = UUID.randomUUID();
        OutboxEventEntity event = event(payment.getOrderId());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            inbox.tryInsert(eventId, "CreatePaymentRequested", payment.getOrderId(), NOW);
            payments.saveAndFlush(payment);
            outbox.saveAndFlush(event);
            throw new IllegalStateException("Simulated processing failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(inbox.existsById(eventId)).isFalse();
        assertThat(payments.existsById(payment.getId())).isFalse();
        assertThat(outbox.existsById(event.getId())).isFalse();
    }

    @Test
    void claimedOutboxEvents_areExclusiveAndRecoveredAfterTimeout() {
        OutboxEventEntity event = outbox.saveAndFlush(event(UUID.randomUUID()));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<OutboxEventEntity> claimed = outbox.claimNewEvents(100);
            assertThat(claimed).extracting(OutboxEventEntity::getId).contains(event.getId());
        });
        assertThat(outbox.findById(event.getId())).get()
                .extracting(OutboxEventEntity::getStatus).isEqualTo(OutboxEventStatus.PROCESSING);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(outbox.claimNewEvents(100)).isEmpty();
            entityManager.createNativeQuery(
                            "UPDATE outbox_events SET processed_at = :time WHERE id = :id")
                    .setParameter("time", NOW.minusSeconds(120))
                    .setParameter("id", event.getId())
                    .executeUpdate();
            outbox.releaseStuckProcessingEvents(NOW.minusSeconds(60));
        });

        assertThat(outbox.findById(event.getId())).get()
                .extracting(OutboxEventEntity::getStatus).isEqualTo(OutboxEventStatus.NEW);
    }

    private boolean insertAfterStart(PaymentEntity payment, CountDownLatch start) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent insert did not start");
        }
        try {
            payments.saveAndFlush(payment);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private PaymentEntity payment(UUID orderId, int attemptNumber) {
        return PaymentEntity.create(UUID.randomUUID(), orderId, UUID.randomUUID(), attemptNumber,
                new BigDecimal("150.00"), "RUB", NOW.plusSeconds(300), NOW);
    }

    private OutboxEventEntity event(UUID orderId) {
        return OutboxEventEntity.create(UUID.randomUUID(), "Order", orderId,
                "PaymentSucceeded", "{}", NOW);
    }
}
