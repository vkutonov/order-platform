package com.valentin.inventoryservice.config;

import com.valentin.inventoryservice.messaging.exception.EventContractException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.order-events-dlt}")
            String dltTopic
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                        new TopicPartition(
                                dltTopic,
                                record.partition()
                        )
        );

        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(
                Duration.ofSeconds(10)
        );

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        new FixedBackOff(
                                2_000L,
                                2L
                        )
                );

        errorHandler.addNotRetryableExceptions(
                EventContractException.class
        );

        return errorHandler;

    }
}
