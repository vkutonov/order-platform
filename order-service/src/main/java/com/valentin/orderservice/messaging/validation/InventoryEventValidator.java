package com.valentin.orderservice.messaging.validation;

import com.valentin.orderservice.messaging.event.InventoryEvent;
import com.valentin.orderservice.messaging.exception.InvalidEventContractException;
import com.valentin.orderservice.messaging.exception.UnsupportedEventVersionException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;



@RequiredArgsConstructor
@Component
public class InventoryEventValidator {

    public static final int SUPPORTED_VERSION = 1;

    private final Validator validator;

    public void validate(InventoryEvent event) {
        var violations = validator.validate(event);

        if (!violations.isEmpty()) {
            throw new InvalidEventContractException(
                    "Validation failed: " + violations
            );
        }

        if (event.eventVersion() != SUPPORTED_VERSION) {
            throw new UnsupportedEventVersionException(
                    event.eventVersion(),
                    SUPPORTED_VERSION
            );
        }

    }
}
