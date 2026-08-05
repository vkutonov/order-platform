package com.valentin.inventoryservice.messaging.consumer;

import com.valentin.inventoryservice.logic.OrderCreatedEventHandler;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
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

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.info(
                "Received OrderCreatedEvent: key={}, topic={}, partition={}, offset={}",
                record.key(),
                record.topic(),
                record.partition(),
                record.offset()
        );

        OrderCreatedEvent event = parser.parse(record.value());

        validator.validate(event);

        handler.handle(event);

    }
}
