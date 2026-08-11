package com.valentin.orderservice.api;

import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.logic.OrderCommandService;
import com.valentin.orderservice.logic.OrderQueryService;
import com.valentin.orderservice.logic.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable UUID id
    ) {
        OrderResponse response = orderQueryService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<OrderSummaryResponseList> getOrdersByUserId(
            @PathVariable UUID userId
    ) {
        OrderSummaryResponseList orders = orderQueryService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderHistoryResponse>> getOrderHistoryById(
            @PathVariable UUID id
    ) {
        List<OrderHistoryResponse> response = orderQueryService.getOrderHistoryById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Temporary endpoints for simulating Service events.
    // Will be replaced by Kafka consumer.

    @PostMapping("/{id}/payment-success")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPayment(@PathVariable UUID id) {
        orderCommandService.markPaymentSucceeded(id);
    }

    @PostMapping("/{id}/payment-failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void paymentFailed(@PathVariable UUID id) {
        orderCommandService.markPaymentFailed(id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(@PathVariable UUID id) {
        orderCommandService.cancelOrder(id);
    }
}
