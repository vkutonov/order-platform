package com.valentin.orderservice.messaging.consumer;

import com.valentin.orderservice.logic.PaymentEventHandler;
import com.valentin.orderservice.messaging.exception.InvalidEventContractException;
import com.valentin.orderservice.messaging.parser.PaymentEventParser;
import com.valentin.orderservice.messaging.validation.PaymentEventValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentEventParser parser;
    private final PaymentEventValidator validator;
    private final PaymentEventHandler handler;

    @KafkaListener(
            id = "paymentResults",
            topics = "${app.kafka.topics.payment-events}",
            groupId = "${app.kafka.consumer.payment-group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        parser.parse(record.value()).ifPresent(event -> {

            validator.validate(event);

            if (!event.orderId().toString().equals(record.key())) {
                throw new InvalidEventContractException("Payment event key must match orderId");
            }

            log.debug("Received payment result: eventId={}, paymentId={}, orderId={}, topic={}, partition={}, offset={}",
                    event.eventId(), event.paymentId(), event.orderId(), record.topic(), record.partition(), record.offset());

            handler.handle(event);
        });
    }
}
