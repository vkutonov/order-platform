package com.valentin.paymentservice.messaging;

import com.valentin.paymentservice.config.KafkaConsumerConfig;
import com.valentin.paymentservice.domain.OutboxEventEntity;
import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import com.valentin.paymentservice.messaging.outbox.OutboxKafkaPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(KafkaInfrastructureIntegrationTest.Config.class)
@EmbeddedKafka(partitions = 3, topics = {"order.events", "payment.events", "order.events.payment-service.dlt"})
@TestPropertySource(properties = "app.kafka.topics.order-events-dlt=order.events.payment-service.dlt")
@DirtiesContext
class KafkaInfrastructureIntegrationTest {

    private static final String ORDERS = "order.events";
    private static final String EVENTS = "payment.events";
    private static final String DLT = "order.events.payment-service.dlt";

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private DefaultErrorHandler errorHandler;

    @ParameterizedTest
    @CsvSource({"contract,1", "temporary,3"})
    void rejectedRecord_reachesDltOnOriginalPartitionAfterConfiguredAttempts(String failure, int expectedAttempts)
            throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String key = UUID.randomUUID().toString();
        try (KafkaConsumer<String, String> dltConsumer = consumer()) {
            broker.consumeFromAnEmbeddedTopic(dltConsumer, true, DLT);
            KafkaMessageListenerContainer<String, String> listener = listener(record -> {
                attempts.incrementAndGet();
                if (failure.equals("contract")) {
                    throw new InvalidEventContractException("Invalid order event");
                }
                throw new IllegalStateException("Temporary database failure");
            });
            try {
                listener.start();
                ContainerTestUtils.waitForAssignment(listener, 3);
                kafkaTemplate.send(new ProducerRecord<>(ORDERS, 2, key, "{}")).get(10, TimeUnit.SECONDS);

                ConsumerRecord<String, String> record = receive(dltConsumer, key);

                assertThat(record.topic()).isEqualTo(DLT);
                assertThat(record.partition()).isEqualTo(2);
                assertThat(record.value()).isEqualTo("{}");
                assertThat(attempts).hasValue(expectedAttempts);
            } finally {
                listener.stop();
            }
        }
    }

    @Test
    void transientFailure_canRecoverThroughRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch processed = new CountDownLatch(1);
        KafkaMessageListenerContainer<String, String> listener = listener(record -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("Database temporarily unavailable");
            }
            processed.countDown();
        });
        try {
            listener.start();
            ContainerTestUtils.waitForAssignment(listener, 3);
            kafkaTemplate.send(ORDERS, UUID.randomUUID().toString(), "{}").get(10, TimeUnit.SECONDS);

            assertThat(processed.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts).hasValue(3);
        } finally {
            listener.stop();
        }
    }

    @Test
    void outboxPublisherDeliversPaymentResultsWithOrderIdAsKey() throws Exception {
        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "paymentEventsTopic", EVENTS);
        try (KafkaConsumer<String, String> eventConsumer = consumer()) {
            broker.consumeFromAnEmbeddedTopic(eventConsumer, true, EVENTS);
            for (String eventType : new String[]{"PaymentSucceeded", "PaymentFailed", "PaymentRefunded", "PaymentRefundFailed"}) {
                UUID orderId = UUID.randomUUID();
                String payload = "{\"eventType\":\"" + eventType + "\"}";
                publisher.publish(OutboxEventEntity.create(UUID.randomUUID(), "Order", orderId,
                        eventType, payload, Instant.now())).get(10, TimeUnit.SECONDS);

                ConsumerRecord<String, String> record = receive(eventConsumer, orderId.toString());
                assertThat(record.topic()).isEqualTo(EVENTS);
                assertThat(record.value()).isEqualTo(payload);
            }
        }
    }

    private KafkaMessageListenerContainer<String, String> listener(MessageListener<String, String> callback) {
        var properties = KafkaTestUtils.consumerProps(broker, UUID.randomUUID().toString(), false);
        properties.put("auto.offset.reset", "latest");
        var factory = new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), new StringDeserializer());
        var containerProperties = new ContainerProperties(ORDERS);
        containerProperties.setMessageListener(callback);
        var listener = new KafkaMessageListenerContainer<>(factory, containerProperties);
        listener.setCommonErrorHandler(errorHandler);
        return listener;
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(KafkaTestUtils.consumerProps(broker, UUID.randomUUID().toString(), false),
                new StringDeserializer(), new StringDeserializer());
    }

    private ConsumerRecord<String, String> receive(KafkaConsumer<String, String> consumer, String key) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Kafka record did not arrive: " + key);
    }

    @Configuration
    @Import(KafkaConsumerConfig.class)
    static class Config {

        @Bean
        DefaultKafkaProducerFactory<String, String> producerFactory(EmbeddedKafkaBroker broker) {
            var yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application.yaml"));
            var application = yaml.getObject();
            var properties = KafkaTestUtils.producerProps(broker);
            for (String name : new String[]{"delivery.timeout.ms", "request.timeout.ms", "max.block.ms"}) {
                properties.put(name, application.getProperty("spring.kafka.producer.properties[" + name + "]"));
            }
            return new DefaultKafkaProducerFactory<>(properties,
                    new StringSerializer(), new StringSerializer());
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate(DefaultKafkaProducerFactory<String, String> factory) {
            return new KafkaTemplate<>(factory);
        }
    }
}
