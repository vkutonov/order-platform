package com.valentin.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.List;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ClientErrorResponse> handleOrderNotFound(
            OrderNotFoundException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        log.warn(
                "Order not found: path={}, message={}",
                request.getRequestURI(),
                exception.getMessage()
        );

        ClientErrorResponse response = clientErrorResponse(
                status,
                "ORDER_NOT_FOUND",
                exception.getMessage(),
                request,
                List.of()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    public ResponseEntity<ClientErrorResponse> handleInvalidOrderStatusTransition(
            InvalidOrderStatusTransitionException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;

        log.warn(
                "Invalid order status transition: path={}, orderId={}, currentStatus={}, requestedStatus={}",
                request.getRequestURI(),
                exception.getOrderId(),
                exception.getCurrentStatus(),
                exception.getRequestedStatus()
        );

        ClientErrorResponse response = clientErrorResponse(
                status,
                "INVALID_ORDER_STATUS_TRANSITION",
                "Order cannot be moved from %s to %s"
                        .formatted(exception.getCurrentStatus(), exception.getRequestedStatus()),
                request,
                List.of()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ClientErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorResponse(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        log.warn(
                "Request validation failed: path={}, fieldErrorsCount={}",
                request.getRequestURI(),
                fieldErrors.size()
        );

        ClientErrorResponse response = clientErrorResponse(
                status,
                "VALIDATION_FAILED",
                "Request validation failed",
                request,
                fieldErrors
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ClientErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        log.warn(
                "Invalid request body: path={}, message={}",
                request.getRequestURI(),
                exception.getMostSpecificCause().getMessage()
        );

        ClientErrorResponse response = clientErrorResponse(
                status,
                "INVALID_REQUEST_BODY",
                "Invalid request body",
                request,
                List.of()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ClientErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        String message = "Invalid value for parameter '" + exception.getName() + "'";

        log.warn(
                "Invalid method argument type: path={}, parameter={}, value={}, requiredType={}",
                request.getRequestURI(),
                exception.getName(),
                exception.getValue(),
                exception.getRequiredType() != null ? exception.getRequiredType().getSimpleName() : null
        );

        ClientErrorResponse response = clientErrorResponse(
                status,
                "INVALID_PARAMETER_VALUE",
                message,
                request,
                List.of()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ClientErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        String message = "Resource not found";

        log.warn(
                "Resource not found: path={}",
                request.getRequestURI()
        );

        ClientErrorResponse response = clientErrorResponse(
                status,
                "RESOURCE_NOT_FOUND",
                message,
                request,
                List.of()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ServerErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unexpected error occurred: path={}",
                request.getRequestURI(),
                exception
        );

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        ServerErrorResponse response = new ServerErrorResponse(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                "Internal server error",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    private ClientErrorResponse clientErrorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldErrorResponse> fieldErrors
    ) {
        return new ClientErrorResponse(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        );
    }
}
