package com.valentin.paymentservice.db;

import com.valentin.paymentservice.domain.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByOrderIdAndAttemptNumber(UUID orderId, int attemptNumber);

    Optional<PaymentEntity> findByProviderPaymentId(String providerPaymentId);
}
