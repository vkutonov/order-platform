package com.valentin.paymentservice.exception;

import com.valentin.paymentservice.api.PaymentController;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.logic.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private PaymentService payments;
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        payments = mock(PaymentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(payments))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void expectedErrorsKeepTheirHttpStatus() throws Exception {
        UUID id = UUID.randomUUID();
        String confirmation = "/api/payments/" + id + "/confirm";
        when(payments.confirm(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"))
                .thenThrow(new InvalidPaymentStatusException(id, PaymentStatus.SUCCEEDED, PaymentStatus.PENDING))
                .thenThrow(new OptimisticLockingFailureException("Concurrent update"));

        mvc.perform(post(confirmation))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Payment not found"));
        mvc.perform(post(confirmation)).andExpect(status().isConflict());
        mvc.perform(post(confirmation)).andExpect(status().isConflict());
        mvc.perform(post("/api/payments/invalid-id/confirm")).andExpect(status().isBadRequest());
        mvc.perform(get(confirmation)).andExpect(status().isMethodNotAllowed());
        verify(payments, times(3)).confirm(id);
        verifyNoMoreInteractions(payments);
    }

    @Test
    void unexpectedFailureReturnsGenericProblemDetails() throws Exception {
        when(payments.getAll()).thenThrow(new IllegalStateException("Internal database details"));

        var result = mvc.perform(get("/api/payments"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Internal server error"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Internal database details");
    }
}
