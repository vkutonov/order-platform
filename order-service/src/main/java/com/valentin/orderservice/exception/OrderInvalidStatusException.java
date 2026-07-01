package com.valentin.orderservice.exception;

public class OrderInvalidStatusException extends RuntimeException{

    public OrderInvalidStatusException (String message) {
        super(message);
    }
}
