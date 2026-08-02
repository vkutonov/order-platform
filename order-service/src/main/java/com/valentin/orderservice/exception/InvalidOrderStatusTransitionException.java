package com.valentin.orderservice.exception;

import com.valentin.orderservice.domain.dictionary.OrderStatus;
import lombok.Getter;

import java.util.UUID;

@Getter
public final class InvalidOrderStatusTransitionException extends OrderServiceException {

    private final UUID orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus requestedStatus;

    public InvalidOrderStatusTransitionException(
            UUID orderId,
            OrderStatus currentStatus,
            OrderStatus requestedStatus
    ) {
        super(
                ApiErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                "Order cannot be moved from %s to %s"
                        .formatted(currentStatus, requestedStatus)
        );

        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }
}
