package com.valentin.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({InvalidPaymentStatusException.class, InvalidRefundStatusException.class})
    public ResponseEntity<Object> handleInvalidStatus(RuntimeException exception, WebRequest request) {
        return handleExceptionInternal(exception,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()),
                HttpHeaders.EMPTY, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Object> handleConcurrentModification(ConcurrencyFailureException exception, WebRequest request) {
        return handleExceptionInternal(exception,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Payment was modified concurrently. Retry the request"),
                HttpHeaders.EMPTY, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception,
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
                HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        if (status.is5xxServerError()) {
            log.error("Unexpected payment API error: request={}, status={}",
                    request.getDescription(false), status.value(), exception);
        } else if (status.value() == HttpStatus.NOT_FOUND.value()) {
            log.debug("Payment API resource not found: request={}", request.getDescription(false));
        } else if (exception instanceof InvalidPaymentStatusException || exception instanceof InvalidRefundStatusException) {
            log.warn("Payment status transition rejected: request={}, status={}, reason={}",
                    request.getDescription(false), status.value(), exception.getMessage());
        } else {
            log.warn("Payment API request rejected: request={}, status={}, exceptionType={}",
                    request.getDescription(false), status.value(), exception.getClass().getSimpleName());
        }
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }
}
