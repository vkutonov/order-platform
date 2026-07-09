package com.valentin.orderservice.exception;

import com.valentin.orderservice.domain.OrderStatus;
import lombok.Getter;

import java.util.UUID;
@Getter
public class InvalidOrderStatusTransitionException extends RuntimeException{

    private final UUID orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus requestedStatus;

    public InvalidOrderStatusTransitionException(
            UUID orderId,
            OrderStatus currentStatus,
            OrderStatus requestedStatus
    ) {
        super("Invalid order status transition: orderId=%s, currentStatus=%s, requestedStatus=%s"
                .formatted(orderId, currentStatus, requestedStatus));

        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;


    }
}
