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

    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";

    private final Clock clock;

    @ExceptionHandler(OrderServiceException.class)
    public ResponseEntity<ClientErrorResponse> handleOrderServiceException(
            OrderServiceException exception,
            HttpServletRequest request
    ) {
        ApiErrorCode errorCode = exception.getErrorCode();

        log.warn(
                "Order operation failed: path={}, code={}, message={}",
                request.getRequestURI(),
                errorCode,
                exception.getMessage()
        );

        return clientErrorResponse(
                errorCode,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ClientErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
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

        return clientErrorResponse(
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ClientErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Invalid request body: path={}, message={}",
                request.getRequestURI(),
                exception.getMostSpecificCause().getMessage()
        );

        return clientErrorResponse(
                ApiErrorCode.INVALID_REQUEST_BODY,
                "Invalid request body",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ClientErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        String message = "Invalid value for parameter '" + exception.getName() + "'";

        log.warn(
                "Invalid method argument type: path={}, parameter={}, value={}, requiredType={}",
                request.getRequestURI(),
                exception.getName(),
                exception.getValue(),
                exception.getRequiredType() != null ? exception.getRequiredType().getSimpleName() : null
        );

        return clientErrorResponse(
                ApiErrorCode.INVALID_PARAMETER_VALUE,
                message,
                request,
                List.of()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ClientErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        String message = "Resource not found";

        log.warn(
                "Resource not found: path={}",
                request.getRequestURI()
        );

        return clientErrorResponse(
                ApiErrorCode.RESOURCE_NOT_FOUND,
                message,
                request,
                List.of()
        );
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
                INTERNAL_SERVER_ERROR_MESSAGE,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    private ResponseEntity<ClientErrorResponse> clientErrorResponse(
            ApiErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<FieldErrorResponse> fieldErrors
    ) {
        HttpStatus status = errorCode.status();
        ClientErrorResponse response = new ClientErrorResponse(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                errorCode.name(),
                message,
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(status).body(response);
    }
}
