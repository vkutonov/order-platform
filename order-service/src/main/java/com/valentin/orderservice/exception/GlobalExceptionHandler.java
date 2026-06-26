package com.valentin.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(
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

        ValidationErrorResponse response = new ValidationErrorResponse(
                Instant.now(),
                status.value(),
                "Validation Failed",
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Invalid request body",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        String message = "Invalid value for parameter '" + exception.getName() + "'";

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        String message = "Resource not found";

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected error occurred", exception);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Internal server error",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}