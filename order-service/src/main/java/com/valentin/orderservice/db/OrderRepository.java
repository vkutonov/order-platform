package com.valentin.orderservice.db;

import com.valentin.orderservice.domain.OrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            """
        select o
        from OrderEntity o
        where o.userId = :userId
        order by o.createdAt asc
        """
    )
    List<OrderEntity> findByUserIdOrderByCreatedAtAsc(@Param("userId") UUID userId);
}
