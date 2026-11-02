package com.valentin.paymentservice.db;

import com.valentin.paymentservice.domain.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, UUID> {

    Optional<RefundEntity> findByPaymentId(UUID paymentId);
}
