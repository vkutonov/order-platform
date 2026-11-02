package com.valentin.paymentservice.logic;

import com.valentin.paymentservice.db.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.payment.expiration.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentExpirationScheduler {

    private final PaymentRepository payments;
    private final PaymentService service;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.payment.expiration.check-delay-ms:5000}",
            initialDelayString = "${app.payment.expiration.check-delay-ms:5000}")
    public void expirePending() {
        for (var orderId : payments.findExpiredOrderIds(clock.instant(), PageRequest.of(0, 50))) {
            try {
                service.expire(orderId);
            } catch (RuntimeException exception) {
                log.error("Payment expiration failed, will retry: orderId={}", orderId, exception);
            }
        }
    }
}
