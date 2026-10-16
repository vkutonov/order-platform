package com.valentin.paymentservice.api;

import com.valentin.paymentservice.api.dto.MockPaymentRequest;
import com.valentin.paymentservice.api.dto.PaymentResponse;
import com.valentin.paymentservice.domain.dictionary.PaymentStatus;
import com.valentin.paymentservice.logic.MockPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MockPaymentController {

    private final MockPaymentService payments;

    @PostMapping("/api/mock/payments")
    public PaymentResponse create(@Valid @RequestBody MockPaymentRequest request) {
        return payments.create(request);
    }

    @GetMapping("/api/mock/payments/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return payments.get(id);
    }

    @GetMapping(value = "/mock-payments/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable UUID id) {
        PaymentResponse payment = payments.get(id);
        String message = switch (payment.status()) {
            case CREATING -> "Подготовка оплаты";
            case PENDING -> "Ожидает оплаты";
            case SUCCEEDED -> "Оплата выполнена";
            case FAILED -> "SESSION_EXPIRED".equals(payment.failureCode()) ? "Срок оплаты истёк" : "В оплате отказано";
        };
        String buttons = payment.status() == PaymentStatus.PENDING ? """
                <form method="post" action="/mock-payments/%s/pay"><button>Оплатить</button></form>
                <form method="post" action="/mock-payments/%s/decline"><button>Отклонить</button></form>
                """.formatted(id, id) : "";
        String html = """
                <!doctype html>
                <html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>Оплата</title>
                <style>body{font:18px system-ui;max-width:480px;margin:64px auto;padding:24px}
                form{display:inline-block;margin:8px 8px 0 0}button{font:inherit;padding:10px 20px;cursor:pointer}</style>
                </head><body><p>Демонстрационная оплата</p><h1>%s %s</h1><p>%s</p>%s</body></html>
                """.formatted(payment.totalPrice().toPlainString(), payment.currency(), message, buttons);
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore()).body(html);
    }

    @PostMapping("/mock-payments/{id}/pay")
    public ResponseEntity<Void> pay(@PathVariable UUID id) {
        return complete(id, true);
    }

    @PostMapping("/mock-payments/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable UUID id) {
        return complete(id, false);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflict() {
        return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Payment ID, attempt number or active payment already exists")).build();
    }

    private ResponseEntity<Void> complete(UUID id, boolean paid) {
        payments.complete(id, paid);
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/mock-payments/" + id)).build();
    }
}
