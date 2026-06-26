package com.valentin.orderservice.api;

import com.valentin.orderservice.dto.CreateOrderRequest;
import com.valentin.orderservice.dto.OrderResponse;
import com.valentin.orderservice.logic.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        log.info("LOG: called createOrder");
        OrderResponse response = orderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
