package com.valentin.paymentservice.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final Clock clock;

    @ExceptionHandler({InvalidPaymentStatusException.class, InvalidRefundStatusException.class})
    public ResponseEntity<Object> handleInvalidStatus(RuntimeException exception, WebRequest request) {
        return handleExceptionInternal(exception, null, HttpHeaders.EMPTY, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Object> handleConcurrentModification(ConcurrencyFailureException exception, WebRequest request) {
        return handleExceptionInternal(exception, null, HttpHeaders.EMPTY, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        Object response;
        if (status.is5xxServerError()) {
            log.error("Unexpected payment API error: path={}, status={}", path, status.value(), exception);
            response = new ServerErrorResponse(clock.instant(), status.value(), ApiErrorCode.INTERNAL_SERVER_ERROR.name(),
                    "Internal server error", path);
        } else {
            ApiErrorCode code = errorCode(exception, status);
            String message = errorMessage(exception, code);
            List<FieldErrorResponse> fields = exception instanceof MethodArgumentNotValidException invalid
                    ? invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage())).toList()
                    : List.of();

            if (status.value() == HttpStatus.NOT_FOUND.value()) {
                log.debug("Payment API resource not found: path={}, code={}", path, code);
            } else {
                log.warn("Payment API request rejected: path={}, status={}, code={}, message={}",
                        path, status.value(), code, message);
            }
            response = new ClientErrorResponse(clock.instant(), status.value(), code.name(), message, path, fields);
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(exception, response, responseHeaders, status, request);
    }

    private ApiErrorCode errorCode(Exception exception, HttpStatusCode status) {
        if (exception instanceof InvalidPaymentStatusException) {
            return ApiErrorCode.INVALID_PAYMENT_STATUS_TRANSITION;
        }
        if (exception instanceof InvalidRefundStatusException) {
            return ApiErrorCode.INVALID_REFUND_STATUS_TRANSITION;
        }
        if (exception instanceof ConcurrencyFailureException) {
            return ApiErrorCode.CONCURRENT_PAYMENT_MODIFICATION;
        }
        if (exception instanceof MethodArgumentNotValidException) {
            return ApiErrorCode.VALIDATION_FAILED;
        }
        if (exception instanceof MethodArgumentTypeMismatchException) {
            return ApiErrorCode.INVALID_PARAMETER_VALUE;
        }
        if (exception instanceof ResponseStatusException) {
            if (status.value() == HttpStatus.NOT_FOUND.value()) {
                return ApiErrorCode.PAYMENT_NOT_FOUND;
            }
            if (status.value() == HttpStatus.CONFLICT.value()) {
                return ApiErrorCode.PAYMENT_DETAILS_CONFLICT;
            }
        }
        return switch (status.value()) {
            case 400 -> ApiErrorCode.INVALID_REQUEST_BODY;
            case 404 -> ApiErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ApiErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ApiErrorCode.NOT_ACCEPTABLE;
            case 415 -> ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ApiErrorCode.REQUEST_FAILED;
        };
    }

    private String errorMessage(Exception exception, ApiErrorCode code) {
        return switch (code) {
            case PAYMENT_NOT_FOUND -> "Payment not found";
            case PAYMENT_DETAILS_CONFLICT -> "Order payment already exists with other details";
            case INVALID_PAYMENT_STATUS_TRANSITION, INVALID_REFUND_STATUS_TRANSITION -> exception.getMessage();
            case CONCURRENT_PAYMENT_MODIFICATION -> "Payment was modified concurrently. Retry the request";
            case VALIDATION_FAILED -> "Request validation failed";
            case INVALID_PARAMETER_VALUE -> "Invalid value for parameter '%s'"
                    .formatted(((MethodArgumentTypeMismatchException) exception).getName());
            case INVALID_REQUEST_BODY -> "Request body is missing or malformed";
            case RESOURCE_NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Request method is not supported";
            case UNSUPPORTED_MEDIA_TYPE -> "Content type is not supported";
            case NOT_ACCEPTABLE -> "Requested response type is not supported";
            default -> "Request failed";
        };
    }
}
