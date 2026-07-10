package com.valentin.orderservice.db;

import com.valentin.orderservice.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

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
