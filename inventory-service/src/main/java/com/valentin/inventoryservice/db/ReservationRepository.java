package com.valentin.inventoryservice.db;

import com.valentin.inventoryservice.domain.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

    Optional<ReservationEntity> findByOrderId(UUID orderId);
}
