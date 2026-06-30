package com.valentin.orderservice.db;

import com.valentin.orderservice.domain.OrderHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderHistoryRepository extends JpaRepository<OrderHistoryEntity, UUID> {

    @Query("""
            select h
            from OrderHistoryEntity h
            where h.order.id = :orderId
            order by h.createdAt asc
    """)
    List<OrderHistoryEntity> findOrderHistoryByIdByCreatedTimeAsc(@Param("orderId") UUID orderId);
}
