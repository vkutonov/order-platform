package com.valentin.orderservice.db;

import com.valentin.orderservice.domain.PaymentResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentResultRepository extends JpaRepository<PaymentResultEntity, UUID> {
}
