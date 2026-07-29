package com.valentin.inventoryservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";

    private final Clock clock;

    @ExceptionHandler(InventoryServiceException.class)
    public ResponseEntity<ClientErrorResponse> handleInventoryServiceException(
            InventoryServiceException exception,
            HttpServletRequest request
    ) {
        ApiErrorCode errorCode = exception.getErrorCode();

        log.warn(
                "Inventory operation failed: path={}, code={}, message={}",
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

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ClientErrorResponse> handleConcurrentStockModification(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        ApiErrorCode errorCode = ApiErrorCode.CONCURRENT_STOCK_MODIFICATION;

        log.warn(
                "Concurrent stock modification: path={}, exceptionType={}",
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );

        return clientErrorResponse(
                errorCode,
                "Inventory item was modified concurrently. Retry the request",
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
    public ResponseEntity<ClientErrorResponse> handleUnreadableRequestBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Request body is missing or malformed: path={}, exceptionType={}",
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );

        return clientErrorResponse(
                ApiErrorCode.INVALID_REQUEST_BODY,
                "Request body is missing or malformed",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ClientErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Invalid method argument type: path={}, parameter={}, value={}, requiredType={}",
                request.getRequestURI(),
                exception.getName(),
                exception.getValue(),
                exception.getRequiredType() != null ? exception.getRequiredType().getSimpleName() : null
        );

        return clientErrorResponse(
                ApiErrorCode.INVALID_PARAMETER_VALUE,
                "Invalid value for parameter '%s'".formatted(exception.getName()),
                request,
                List.of()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ClientErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Resource not found: path={}, exceptionType={}",
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );

        return clientErrorResponse(
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Resource not found",
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

        return ResponseEntity.status(status).body(response);
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
