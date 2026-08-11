package com.valentin.orderservice.messaging.validation;

import com.valentin.orderservice.messaging.event.InventoryReservationFailedEvent;
import com.valentin.orderservice.messaging.event.InventoryReservedEvent;
import com.valentin.orderservice.messaging.exception.InvalidEventContractException;
import com.valentin.orderservice.messaging.exception.UnsupportedEventVersionException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryEventValidatorTest {

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RESERVATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T10:00:00Z");

    private ValidatorFactory validatorFactory;
    private InventoryEventValidator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = new InventoryEventValidator(validatorFactory.getValidator());
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validate_whenReservedEventIsValid_shouldPassValidation() {
        assertThatCode(() -> validator.validate(reservedEvent(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenEventVersionIsUnsupported_shouldRejectEvent() {
        assertThatThrownBy(() -> validator.validate(reservedEvent(2)))
                .isInstanceOf(UnsupportedEventVersionException.class);
    }

    @Test
    void validate_whenFailureCodeIsMissing_shouldRejectEvent() {
        InventoryReservationFailedEvent event = new InventoryReservationFailedEvent(
                EVENT_ID,
                "InventoryReservationFailedEvent",
                1,
                ORDER_ID,
                RESERVATION_ID,
                null,
                Map.of("source", "inventory-service"),
                OCCURRED_AT
        );

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(InvalidEventContractException.class)
                .hasMessageContaining("failureCode");
    }

    private InventoryReservedEvent reservedEvent(int eventVersion) {
        return new InventoryReservedEvent(
                EVENT_ID,
                "InventoryReservedEvent",
                eventVersion,
                ORDER_ID,
                RESERVATION_ID,
                Map.of("source", "inventory-service"),
                OCCURRED_AT
        );
    }
}
