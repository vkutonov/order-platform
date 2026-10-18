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

    @PostMapping("/{id}/confirm")
    public PaymentResponse confirm(@PathVariable UUID id) {
        return payments.confirm(id);
    }
}
