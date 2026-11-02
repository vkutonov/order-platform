package com.valentin.paymentservice.messaging.consumer;

import com.valentin.paymentservice.logic.OrderReadyForPaymentEventHandler;
import com.valentin.paymentservice.messaging.parser.PaymentMessageParser;
import com.valentin.paymentservice.messaging.validation.PaymentMessageValidator;
import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReadyForPaymentEventListener {

    private final PaymentMessageParser parser;
    private final PaymentMessageValidator validator;
    private final OrderReadyForPaymentEventHandler handler;

    @KafkaListener(
            id = "orderReadyForPayment",
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        var cancellation = parser.parseCancellation(record.value());
        if (cancellation.isPresent()) {
            var event = cancellation.get();
            event.validate(record.key());
            handler.handleCancellation(event);
            return;
        }
        parser.parseOrderEvent(record.value()).ifPresent(event -> {
            validator.validateOrderEvent(event);
            if (!event.orderId().toString().equals(record.key())) {
                throw new InvalidEventContractException("Kafka key must match orderId");
            }
            log.debug("Received order readiness: eventId={}, orderId={}, topic={}, partition={}, offset={}",
                    event.eventId(), event.orderId(), record.topic(), record.partition(), record.offset());
            handler.handle(event);
        });
    }
}
