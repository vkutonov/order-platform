package com.valentin.inventoryservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");
    private static final UUID PRODUCT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/products/00000000-0000-0000-0000-000000000001/stock");
    }

    @Test
    void productNotFoundReturnsInventorySpecificError() {
        var response = handler.handleInventoryServiceException(
                new ProductNotFoundException(PRODUCT_ID),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(response.getBody().message())
                .isEqualTo("Product not found: productId=" + PRODUCT_ID);
        assertThat(response.getBody().timestamp()).isEqualTo(NOW);
    }

    @Test
    void insufficientStockReturnsConflict() {
        var response = handler.handleInventoryServiceException(
                InsufficientStockException.forAvailableStock(PRODUCT_ID, 3, 2),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void concurrentModificationReturnsRetryableConflictWithoutInternalDetails() {
        var response = handler.handleConcurrentStockModification(
                new OptimisticLockingFailureException("Internal persistence details"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONCURRENT_STOCK_MODIFICATION");
        assertThat(response.getBody().message())
                .isEqualTo("Inventory item was modified concurrently. Retry the request")
                .doesNotContain("Internal persistence details");
    }

    @Test
    void malformedRequestBodyReturnsBadRequestWithoutParserDetails() {
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);

        var response = handler.handleUnreadableRequestBody(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST_BODY");
        assertThat(response.getBody().message()).isEqualTo("Request body is missing or malformed");
    }
}
