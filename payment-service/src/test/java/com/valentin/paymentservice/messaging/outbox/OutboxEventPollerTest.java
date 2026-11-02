package com.valentin.paymentservice.messaging.outbox;

import com.valentin.paymentservice.config.OutboxExecutorConfig;
import com.valentin.paymentservice.domain.OutboxEventEntity;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPollerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final String TOPIC = "payment.events";

    @Mock private OutboxEventService outbox;
    @Mock private KafkaTemplate<String, String> kafka;
    @Mock private SendResult<String, String> result;
    @Mock private RecordMetadata metadata;

    private AnnotationConfigApplicationContext context;
    private OutboxEventPoller poller;

    @BeforeEach
    void prepare() {
        context = new AnnotationConfigApplicationContext();
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "app.outbox.polling.batch-size=50",
                "app.outbox.polling.processing-timeout-ms=60000",
                "app.kafka.topics.payment-events=" + TOPIC);
        context.registerBean(KafkaTemplate.class, () -> kafka);
        context.registerBean(OutboxEventService.class, () -> outbox);
        context.registerBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));
        context.registerBean("otherExecutor", Executor.class, () -> Runnable::run);
        context.register(OutboxExecutorConfig.class, OutboxKafkaPublisher.class, OutboxEventPoller.class);
        context.refresh();
        poller = context.getBean(OutboxEventPoller.class);
        when(result.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn(TOPIC);
        doAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("outbox-callback-");
            return null;
        }).when(outbox).markPublished(any(), any());
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void batchIsSentAsynchronously_andSlowDatabaseWorkDoesNotBlockKafkaOrAnotherEvent() throws Exception {
        var first = event();
        var second = event();
        var firstAcknowledgement = new CompletableFuture<SendResult<String, String>>();
        var secondAcknowledgement = new CompletableFuture<SendResult<String, String>>();
        var secondSent = new CountDownLatch(1);
        var databaseStarted = new CountDownLatch(1);
        var releaseDatabase = new CountDownLatch(1);
        when(outbox.claimNewEvents(50)).thenReturn(List.of(first, second));
        when(kafka.send(TOPIC, first.getAggregateId().toString(), first.getPayload()))
                .thenReturn(firstAcknowledgement);
        when(kafka.send(TOPIC, second.getAggregateId().toString(), second.getPayload()))
                .thenAnswer(invocation -> {
                    secondSent.countDown();
                    return secondAcknowledgement;
                });
        doAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("outbox-callback-");
            databaseStarted.countDown();
            if (!releaseDatabase.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Database operation did not finish");
            }
            return null;
        }).when(outbox).markPublished(first.getId(), NOW);

        try (var scheduler = Executors.newSingleThreadExecutor();
             var brokerCallback = Executors.newSingleThreadExecutor(task -> new Thread(task, "kafka-test-callback"))) {
            var polling = scheduler.submit(poller::pollAndPublish);
            try {
                assertThat(secondSent.await(5, TimeUnit.SECONDS)).isTrue();
                verify(outbox, never()).markPublished(first.getId(), NOW);
                assertThat(brokerCallback.submit(() -> firstAcknowledgement.complete(result))
                        .get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(databaseStarted.await(5, TimeUnit.SECONDS)).isTrue();

                secondAcknowledgement.complete(result);
                verify(outbox, timeout(1000)).markPublished(second.getId(), NOW);
                assertThat(polling.isDone()).isFalse();
            } finally {
                releaseDatabase.countDown();
                firstAcknowledgement.complete(result);
                secondAcknowledgement.complete(result);
            }
            polling.get(5, TimeUnit.SECONDS);
        }

        verify(outbox, never()).markNewAfterFailure(any(), any());
    }

    @Test
    void synchronousAndAsynchronousFailuresAreRetried_withoutBlockingAnotherEvent() {
        var failed = event();
        var successful = event();
        var failure = new IllegalStateException("Kafka unavailable");
        when(outbox.claimNewEvents(50))
                .thenReturn(List.of(failed, successful))
                .thenReturn(List.of(failed))
                .thenReturn(List.of(failed));
        when(kafka.send(TOPIC, failed.getAggregateId().toString(), failed.getPayload()))
                .thenThrow(failure)
                .thenReturn(CompletableFuture.failedFuture(failure))
                .thenReturn(CompletableFuture.completedFuture(result));
        when(kafka.send(TOPIC, successful.getAggregateId().toString(), successful.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(result));

        poller.pollAndPublish();
        verify(outbox).markPublished(successful.getId(), NOW);
        verify(outbox, never()).markPublished(failed.getId(), NOW);

        poller.pollAndPublish();
        verify(outbox, times(2)).markNewAfterFailure(failed.getId(), failure.getMessage());
        verify(outbox, never()).markPublished(failed.getId(), NOW);

        poller.pollAndPublish();
        verify(outbox).markPublished(failed.getId(), NOW);
        verify(outbox, times(3)).releaseStuckProcessingEvents(NOW.minusSeconds(60));
        verify(kafka).send(TOPIC, successful.getAggregateId().toString(), successful.getPayload());
    }

    @Test
    void databaseFailureAfterAcknowledgementDoesNotPreventOtherEventsFromBeingPublished() {
        var failed = event();
        var successful = event();
        when(outbox.claimNewEvents(50)).thenReturn(List.of(failed, successful));
        when(kafka.send(TOPIC, failed.getAggregateId().toString(), failed.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(result));
        when(kafka.send(TOPIC, successful.getAggregateId().toString(), successful.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(result));
        doThrow(new IllegalStateException("Database unavailable")).when(outbox).markPublished(failed.getId(), NOW);

        assertThatThrownBy(poller::pollAndPublish).isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("Database unavailable");

        verify(outbox).markPublished(successful.getId(), NOW);
        verify(outbox, never()).markNewAfterFailure(any(), any());
    }

    private OutboxEventEntity event() {
        return OutboxEventEntity.create(UUID.randomUUID(), "Order", UUID.randomUUID(),
                "PaymentSucceeded", "{}", NOW);
    }
}
