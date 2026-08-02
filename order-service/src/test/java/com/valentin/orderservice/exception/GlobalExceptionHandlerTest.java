package com.valentin.orderservice.exception;

import com.valentin.orderservice.domain.dictionary.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");
    private static final UUID ORDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/orders/" + ORDER_ID);
    }

    @Test
    void orderNotFoundReturnsNotFound() {
        var response = handler.handleOrderServiceException(
                new OrderNotFoundException("Order not found id = " + ORDER_ID),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Order not found id = " + ORDER_ID);
        assertThat(response.getBody().timestamp()).isEqualTo(NOW);
    }

    @Test
    void unavailableProductReturnsConflictThroughParentHandler() {
        var response = handler.handleOrderServiceException(
                new ProductUnavailableException(Set.of(ORDER_ID)),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PRODUCT_UNAVAILABLE");
        assertThat(response.getBody().message()).contains(ORDER_ID.toString());
    }

    @Test
    void invalidStatusTransitionReturnsConflict() {
        var response = handler.handleOrderServiceException(
                new InvalidOrderStatusTransitionException(
                        ORDER_ID,
                        OrderStatus.PAID,
                        OrderStatus.CANCELLED
                ),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_ORDER_STATUS_TRANSITION");
        assertThat(response.getBody().message()).isEqualTo("Order cannot be moved from PAID to CANCELLED");
    }

    @Test
    void mixedCurrenciesReturnsUnprocessableContent() {
        var response = handler.handleOrderServiceException(
                new MixedOrderCurrenciesException(Set.of("RUB", "USD")),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MIXED_ORDER_CURRENCIES");
        assertThat(response.getBody().message()).contains("RUB", "USD");
    }
}
