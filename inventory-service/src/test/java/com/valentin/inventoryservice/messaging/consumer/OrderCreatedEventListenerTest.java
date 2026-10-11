package com.valentin.inventoryservice.messaging.consumer;

import com.valentin.inventoryservice.logic.OrderCreatedEventHandler;
import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.exception.InvalidEventContractException;
import com.valentin.inventoryservice.messaging.parser.OrderCreatedEventParser;
import com.valentin.inventoryservice.messaging.validation.OrderCreatedEventValidator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderCreatedEventListenerTest {

    private final OrderCreatedEventHandler handler = mock(OrderCreatedEventHandler.class);
    private final OrderCreatedEventListener listener = new OrderCreatedEventListener(
            new OrderCreatedEventParser(new JsonMapper()), new OrderCreatedEventValidator(), handler
    );

    @Test
    void consume_whenPaymentReadyEvent_shouldSkipInventoryProcessing() {
        listener.consume(record("""
                {
                  "eventType": "OrderReadyForPaymentEvent",
                  "eventVersion": 1,
                  "totalPrice": 250.50,
                  "currency": "RUB"
                }
                """));

        verifyNoInteractions(handler);
    }

    @Test
    void consume_whenOrderCreated_shouldStillReserveInventory() {
        listener.consume(record("""
                {
                  "eventId": "00000000-0000-0000-0000-000000000001",
                  "eventType": "OrderCreatedEvent",
                  "eventVersion": 1,
                  "orderId": "00000000-0000-0000-0000-000000000002",
                  "userId": "00000000-0000-0000-0000-000000000003",
                  "items": [{"productId": "00000000-0000-0000-0000-000000000004", "quantity": 2}],
                  "context": {"source": "order-service"},
                  "occurredAt": "2026-10-11T10:00:00Z"
                }
                """));

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(handler).handle(captor.capture());
        assertThat(captor.getValue().items().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void consume_whenPayloadIsMalformed_shouldStillRejectIt() {
        assertThatThrownBy(() -> listener.consume(record("{bad json")))
                .isInstanceOf(InvalidEventContractException.class);
        verifyNoInteractions(handler);
    }

    @Test
    void consume_whenOrderCreatedContractIsInvalid_shouldStillRejectIt() {
        assertThatThrownBy(() -> listener.consume(record("{\"eventType\":\"OrderCreatedEvent\"}")))
                .isInstanceOf(InvalidEventContractException.class);
        verifyNoInteractions(handler);
    }

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("order.events", 0, 0L, "order-id", payload);
    }
}
