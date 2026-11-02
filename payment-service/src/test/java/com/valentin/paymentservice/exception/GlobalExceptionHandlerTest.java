package com.valentin.paymentservice.exception;

import com.valentin.paymentservice.api.PaymentController;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.domain.dictionary.RefundStatus;
import com.valentin.paymentservice.logic.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private PaymentService payments;
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        payments = mock(PaymentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(payments), new ValidationController())
                .setControllerAdvice(new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC))).build();
    }

    @Test
    void expectedAndFrameworkErrorsUseUnifiedContract_andKeepHttpStatusAndHeaders() throws Exception {
        UUID id = UUID.randomUUID();
        String confirmation = "/api/payments/by-order/" + id + "/confirm";
        when(payments.confirm(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"))
                .thenThrow(new InvalidPaymentStatusException(id, PaymentStatus.SUCCEEDED, PaymentStatus.PENDING))
                .thenThrow(new InvalidRefundStatusException(id, RefundStatus.SUCCEEDED, RefundStatus.PENDING))
                .thenThrow(new OptimisticLockingFailureException("Internal concurrency details"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Order payment already exists with other details"));

        mvc.perform(post(confirmation))
                .andExpect(error(404, "PAYMENT_NOT_FOUND", confirmation))
                .andExpect(jsonPath("$.message").value("Payment not found"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        mvc.perform(post(confirmation)).andExpect(error(409, "INVALID_PAYMENT_STATUS_TRANSITION", confirmation));
        mvc.perform(post(confirmation)).andExpect(error(409, "INVALID_REFUND_STATUS_TRANSITION", confirmation));
        mvc.perform(post(confirmation))
                .andExpect(error(409, "CONCURRENT_PAYMENT_MODIFICATION", confirmation))
                .andExpect(jsonPath("$.message").value("Payment was modified concurrently. Retry the request"));
        mvc.perform(post(confirmation)).andExpect(error(409, "PAYMENT_DETAILS_CONFLICT", confirmation));
        mvc.perform(post("/api/payments/by-order/invalid-id/confirm"))
                .andExpect(error(400, "INVALID_PARAMETER_VALUE", "/api/payments/by-order/invalid-id/confirm"));
        mvc.perform(get(confirmation))
                .andExpect(error(405, "METHOD_NOT_ALLOWED", confirmation))
                .andExpect(header().string("Allow", "POST"));
        mvc.perform(get("/api/payments/unknown"))
                .andExpect(error(404, "RESOURCE_NOT_FOUND", "/api/payments/unknown"));
        mvc.perform(get("/api/payments").accept(MediaType.TEXT_PLAIN))
                .andExpect(error(406, "NOT_ACCEPTABLE", "/api/payments"));

        verify(payments, times(5)).confirm(id);
        verify(payments).getAll();
        verifyNoMoreInteractions(payments);
    }

    @Test
    void invalidRequestsReturnFieldErrorsOrBodyErrorInSameFormat() throws Exception {
        mvc.perform(post("/test-validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"\",\"amount\":0}"))
                .andExpect(error(400, "VALIDATION_FAILED", "/test-validation"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("reference", "amount")))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());
        mvc.perform(post("/test-validation").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(error(400, "INVALID_REQUEST_BODY", "/test-validation"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        mvc.perform(post("/test-validation").contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(error(415, "UNSUPPORTED_MEDIA_TYPE", "/test-validation"));
        verifyNoInteractions(payments);
    }

    @Test
    void unexpectedFailureReturnsUnifiedServerError_withoutInternalDetails() throws Exception {
        when(payments.getAll()).thenThrow(new IllegalStateException("Internal database details"));

        var result = mvc.perform(get("/api/payments"))
                .andExpect(error(500, "INTERNAL_SERVER_ERROR", "/api/payments"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Internal database details");
    }

    private ResultMatcher error(int httpStatus, String code, String path) {
        return result -> {
            status().is(httpStatus).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
            jsonPath("$.timestamp").value(NOW.toString()).match(result);
            jsonPath("$.status").value(httpStatus).match(result);
            jsonPath("$.code").value(code).match(result);
            jsonPath("$.message").isNotEmpty().match(result);
            jsonPath("$.path").value(path).match(result);
            jsonPath("$.type").doesNotExist().match(result);
            jsonPath("$.title").doesNotExist().match(result);
            jsonPath("$.detail").doesNotExist().match(result);
            jsonPath("$.instance").doesNotExist().match(result);
        };
    }

    @RestController
    static class ValidationController {

        @PostMapping("/test-validation")
        public void validate(@Valid @RequestBody ValidatedRequest request) {
        }
    }

    record ValidatedRequest(@NotBlank String reference, @Positive int amount) {
    }
}
