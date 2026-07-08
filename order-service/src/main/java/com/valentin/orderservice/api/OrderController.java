package com.valentin.orderservice.api;

import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.logic.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable UUID id
    ) {
        log.info("LOG: called getOrderById");

        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<OrderSummaryResponseList> getOrdersByUserId(
            @PathVariable UUID userId
    ) {
        log.info("LOG: called getOrdersByUserId");

        OrderSummaryResponseList orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderHistoryResponse>> getOrderHistoryById(
            @PathVariable UUID id
    ) {
        log.info("LOG: called getOrderHistoryById");

        List<OrderHistoryResponse> response = orderService.getOrderHistoryById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        log.info("LOG: called createOrder");

        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Temporary endpoints for simulating Service events.
    // Will be replaced by Kafka consumer.

    @PostMapping("/{id}/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveInventory(@PathVariable UUID id) {
        orderService.reserveInventory(id);
    }

    @PostMapping("/{id}/inventory-failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inventoryReservationFailed(@PathVariable UUID id) {
        orderService.inventoryReservationFailed(id);
    }

    @PostMapping("/{id}/payment-success")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPayment(@PathVariable UUID id) {
        orderService.confirmPayment(id);
    }

    @PostMapping("/{id}/payment-failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void paymentFailed(@PathVariable UUID id) {
        orderService.paymentFailed(id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(@PathVariable UUID id) {
        orderService.cancelOrder(id);
    }
}
