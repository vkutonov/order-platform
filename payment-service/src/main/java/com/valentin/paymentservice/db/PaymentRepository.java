package com.valentin.paymentservice.db;

import com.valentin.paymentservice.domain.PaymentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.orderId = :orderId and p.attemptNumber = :attemptNumber")
    Optional<PaymentEntity> findByOrderIdAndAttemptNumberForUpdate(UUID orderId, int attemptNumber);

    Optional<PaymentEntity> findByOrderIdAndAttemptNumber(UUID orderId, int attemptNumber);

    @Query("select p.orderId from PaymentEntity p where p.status = com.valentin.paymentservice.domain.dictionary.PaymentStatus.PENDING and p.expiresAt <= :now order by p.expiresAt, p.id")
    List<UUID> findExpiredOrderIds(Instant now, Pageable pageable);
}
