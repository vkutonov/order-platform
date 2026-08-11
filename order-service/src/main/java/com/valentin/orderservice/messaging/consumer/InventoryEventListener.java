package com.valentin.orderservice.messaging.consumer;

import com.valentin.orderservice.logic.InventoryEventHandler;
import com.valentin.orderservice.messaging.event.InventoryEvent;
import com.valentin.orderservice.messaging.parser.InventoryEventParser;
import com.valentin.orderservice.messaging.validation.InventoryEventValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {


    private final InventoryEventParser parser;
    private final InventoryEventValidator validator;
    private final InventoryEventHandler handler;

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {

        InventoryEvent event = parser.parse(record.value());

        log.info(
                "Received {}: key={}, topic={}, partition={}, offset={}",
                event.eventType(),
                record.key(),
                record.topic(),
                record.partition(),
                record.offset()
        );

        validator.validate(event);

        handler.handle(event);

    }
}
