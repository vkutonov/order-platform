package com.valentin.inventoryservice.messaging.consumer;

import com.valentin.inventoryservice.logic.OrderCreatedEventHandler;
import com.valentin.inventoryservice.logic.OrderLifecycleEventHandler;
import com.valentin.inventoryservice.messaging.parser.OrderCreatedEventParser;
import com.valentin.inventoryservice.messaging.validation.OrderCreatedEventValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCreatedEventListener {

    private final OrderCreatedEventParser parser;
    private final OrderCreatedEventValidator validator;
    private final OrderCreatedEventHandler handler;
    private final OrderLifecycleEventHandler lifecycleHandler;

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        var lifecycle = parser.parseLifecycle(record.value());
        if (lifecycle.isPresent()) {
            var event = lifecycle.get();
            event.validate(record.key());
            lifecycleHandler.handle(event);
            return;
        }
        log.info(
                "Received order event: key={}, topic={}, partition={}, offset={}",
                record.key(),
                record.topic(),
                record.partition(),
                record.offset()
        );

        parser.parse(record.value()).ifPresent(event -> {
            validator.validate(event);
            handler.handle(event);
        });

    }
}
