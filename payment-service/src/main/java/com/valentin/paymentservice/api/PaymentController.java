package com.valentin.paymentservice.api;

import com.valentin.paymentservice.api.dto.PaymentResponse;
import com.valentin.paymentservice.logic.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService payments;

    @GetMapping
    public List<PaymentResponse> getAll() {
        return payments.getAll();
    }

    @PostMapping("/by-order/{orderId}/confirm")
    public PaymentResponse confirm(@PathVariable UUID orderId) {
        return payments.confirm(orderId);
    }

    @PostMapping("/by-order/{orderId}/reject")
    public PaymentResponse reject(@PathVariable UUID orderId) {
        return payments.reject(orderId);
    }
}
