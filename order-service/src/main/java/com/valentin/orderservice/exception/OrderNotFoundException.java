package com.valentin.orderservice.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}
