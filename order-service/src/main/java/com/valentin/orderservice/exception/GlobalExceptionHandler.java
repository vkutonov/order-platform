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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";

    private static final String MISSING_PRODUCTS_MESSAGE = "Order creation failed, products missed";

    private final Clock clock;

    @ExceptionHandler(OrderServiceException.class)
    public ResponseEntity<ClientErrorResponse> handleOrderServiceException(
            OrderServiceException exception,
            HttpServletRequest request
    ) {
        ApiErrorCode errorCode = exception.getErrorCode();
        List<UUID> missingProductIds = exception instanceof ProductNotFoundException productNotFound
                ? productNotFound.getProductIds().stream().sorted().toList()
                : List.of();
        String message = exception instanceof ProductNotFoundException
                ? MISSING_PRODUCTS_MESSAGE
                : exception.getMessage();

        log.warn(
                "Order operation failed: path={}, code={}, message={}",
                request.getRequestURI(),
                errorCode,
                exception.getMessage()
        );

        return clientErrorResponse(
                errorCode,
                message,
                request,
                List.of(),
                missingProductIds
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


    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ResponseEntity<?> handleInventoryNotFound(
            HttpClientErrorException.NotFound exception,
            HttpServletRequest request
    ) {
        ClientErrorResponse inventoryError;
        try {
            inventoryError = exception.getResponseBodyAs(ClientErrorResponse.class);
        } catch (RestClientException invalidResponse) {
            return handleUnexpectedException(exception, request);
        }

        if (inventoryError == null
                || !ApiErrorCode.PRODUCT_NOT_FOUND.name().equals(inventoryError.code())) {
            return handleUnexpectedException(exception, request);
        }

        List<UUID> missingProductIds = inventoryError.missingProductIds() != null
                ? inventoryError.missingProductIds()
                : List.of();

        log.warn(
                "Product not found: path={}, missingProductIds={}",
                request.getRequestURI(),
                missingProductIds
        );

        return clientErrorResponse(
                ApiErrorCode.PRODUCT_NOT_FOUND,
                MISSING_PRODUCTS_MESSAGE,
                request,
                List.of(),
                missingProductIds
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
                "INTERNAL_SERVER_ERROR",
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
        return clientErrorResponse(errorCode, message, request, fieldErrors, List.of());
    }

    private ResponseEntity<ClientErrorResponse> clientErrorResponse(
            ApiErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<FieldErrorResponse> fieldErrors,
            List<UUID> missingProductIds
    ) {
        HttpStatus status = errorCode.status();
        ClientErrorResponse response = new ClientErrorResponse(
                clock.instant(),
                status.value(),
                errorCode.name(),
                message,
                request.getRequestURI(),
                missingProductIds,
                fieldErrors
        );

        return ResponseEntity.status(status).body(response);
    }
}
