package com.valentin.paymentservice.messaging;

import com.valentin.paymentservice.messaging.event.*;
import com.valentin.paymentservice.messaging.exception.InvalidEventContractException;
import com.valentin.paymentservice.messaging.exception.UnsupportedEventVersionException;
import com.valentin.paymentservice.messaging.parser.PaymentMessageParser;
import com.valentin.paymentservice.messaging.validation.PaymentMessageValidator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class PaymentMessageTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID REFUND_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    private final JsonMapper mapper = new JsonMapper();
    private final PaymentMessageParser parser = new PaymentMessageParser(mapper);
    private final PaymentMessageValidator validator = new PaymentMessageValidator(FACTORY.getValidator());

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @ParameterizedTest
    @MethodSource("messages")
    void contracts_roundTripWithStableIdentifiersAndMetadata(PaymentMessage message) {
        PaymentMessage parsed = parser.parse(mapper.writeValueAsString(message));

        validator.validate(parsed);

        assertThat(parsed).isEqualTo(message);
        assertThat(parsed.eventId()).isEqualTo(EVENT_ID);
        assertThat(parsed.orderId()).isEqualTo(ORDER_ID);
        assertThat(parsed.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(parsed.eventVersion()).isEqualTo(1);
        assertThat(parsed.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void parser_rejectsMalformedAndUnknownContracts() {
        for (String payload : new String[]{null, "", "{", "null", "{}", "{\"eventType\":\"Unknown\"}"}) {
            assertThatThrownBy(() -> parser.parse(payload))
                    .isInstanceOf(InvalidEventContractException.class);
        }
    }

    @Test
    void validator_rejectsUnsupportedVersionAndInconsistentType() {
        assertThatThrownBy(() -> validator.validate(success(PaymentSucceeded.TYPE, 2, AMOUNT, "RUB")))
                .isInstanceOf(UnsupportedEventVersionException.class);
        assertThatThrownBy(() -> validator.validate(success("WrongType", 1, AMOUNT, "RUB")))
                .isInstanceOf(InvalidEventContractException.class);
    }

    @Test
    void validator_rejectsInvalidMoney() {
        assertThatThrownBy(() -> validator.validate(success(PaymentSucceeded.TYPE, 1, BigDecimal.ZERO, "RUB")))
                .isInstanceOf(InvalidEventContractException.class);
        assertThatThrownBy(() -> validator.validate(success(PaymentSucceeded.TYPE, 1, new BigDecimal("1.001"), "RUB")))
                .isInstanceOf(InvalidEventContractException.class);
        assertThatThrownBy(() -> validator.validate(success(PaymentSucceeded.TYPE, 1, AMOUNT, "rub")))
                .isInstanceOf(InvalidEventContractException.class);
    }

    @Test
    void validator_rejectsMissingIdentity() {
        PaymentFailed missingId = PaymentFailed.of(null, ORDER_ID, PAYMENT_ID, AMOUNT, "RUB", "DECLINED", NOW);
        assertThatThrownBy(() -> validator.validate(missingId)).isInstanceOf(InvalidEventContractException.class);
    }

    private PaymentSucceeded success(String type, int version, BigDecimal amount, String currency) {
        return new PaymentSucceeded(EVENT_ID, type, version, ORDER_ID, PAYMENT_ID,
                amount, currency, Map.of(), NOW);
    }

    private static Stream<PaymentMessage> messages() {
        return Stream.of(
                PaymentSucceeded.of(EVENT_ID, ORDER_ID, PAYMENT_ID, AMOUNT, "RUB", NOW),
                PaymentFailed.of(EVENT_ID, ORDER_ID, PAYMENT_ID, AMOUNT, "RUB", "DECLINED", NOW),
                PaymentRefunded.of(EVENT_ID, ORDER_ID, PAYMENT_ID, REFUND_ID, AMOUNT, "RUB", "session-1", "refund-1", NOW),
                PaymentRefundFailed.of(EVENT_ID, ORDER_ID, PAYMENT_ID, REFUND_ID, AMOUNT, "RUB", "session-1", "REFUND_DECLINED", NOW)
        );
    }
}
