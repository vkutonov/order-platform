package com.valentin.inventoryservice.messaging.validation;

import com.valentin.inventoryservice.messaging.event.OrderCreatedEvent;
import com.valentin.inventoryservice.messaging.event.OrderCreatedItem;
import com.valentin.inventoryservice.messaging.exception.InvalidEventContractException;
import com.valentin.inventoryservice.messaging.exception.UnsupportedEventVersionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCreatedEventValidatorTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-05T10:00:00Z");

    private OrderCreatedEventValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderCreatedEventValidator();
    }

    @Test
    void validEventPassesValidation() {
        assertThatCode(() -> validator.validate(validEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    void unsupportedEventTypeIsRejected() {
        OrderCreatedEvent event = event(
                "PaymentCreatedEvent",
                1,
                USER_ID,
                validItems(),
                OCCURRED_AT
        );

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(InvalidEventContractException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void unsupportedEventVersionIsRejected() {
        OrderCreatedEvent event = event(
                "OrderCreatedEvent",
                2,
                USER_ID,
                validItems(),
                OCCURRED_AT
        );

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(UnsupportedEventVersionException.class);
    }

    @Test
    void missingUserIdIsRejected() {
        OrderCreatedEvent event = event(
                "OrderCreatedEvent",
                1,
                null,
                validItems(),
                OCCURRED_AT
        );

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(InvalidEventContractException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void missingProductIdIsRejected() {
        OrderCreatedEvent event = event(
                "OrderCreatedEvent",
                1,
                USER_ID,
                List.of(new OrderCreatedItem(
                        null,
                        "Keyboard",
                        new BigDecimal("5000.00"),
                        "RUB",
                        1
                )),
                OCCURRED_AT
        );

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(InvalidEventContractException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        OrderCreatedEvent event = event(
                "OrderCreatedEvent",
                1,
                USER_ID,
                List.of(new OrderCreatedItem(
                        PRODUCT_ID,
                        "Keyboard",
                        new BigDecimal("5000.00"),
                        "RUB",
                        0
                )),
                OCCURRED_AT
        );

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(InvalidEventContractException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void missingOccurredAtIsRejected() {
        OrderCreatedEvent event = event(
                "OrderCreatedEvent",
                1,
                USER_ID,
                validItems(),
                null
        );

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(InvalidEventContractException.class)
                .hasMessageContaining("occurredAt");
    }

    private OrderCreatedEvent validEvent() {
        return event(
                "OrderCreatedEvent",
                1,
                USER_ID,
                validItems(),
                OCCURRED_AT
        );
    }

    private OrderCreatedEvent event(
            String eventType,
            int eventVersion,
            UUID userId,
            List<OrderCreatedItem> items,
            Instant occurredAt
    ) {
        return new OrderCreatedEvent(
                EVENT_ID,
                eventType,
                eventVersion,
                ORDER_ID,
                userId,
                items,
                Map.of("source", "order-service"),
                occurredAt
        );
    }

    private List<OrderCreatedItem> validItems() {
        return List.of(new OrderCreatedItem(
                PRODUCT_ID,
                "Keyboard",
                new BigDecimal("5000.00"),
                "RUB",
                1
        ));
    }
}
