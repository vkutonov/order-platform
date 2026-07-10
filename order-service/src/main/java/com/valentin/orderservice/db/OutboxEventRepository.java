package com.valentin.orderservice.db;

import com.valentin.orderservice.domain.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    // limit - чтобы не перегружать бд
    // несколько poller'ов могут забрать одни и те же события - решение FOR UPDATE SKIP LOCKED
    @Query(value = """
            UPDATE outbox_events
            SET status = 'PROCESSING',
                processed_at = CURRENT_TIMESTAMP,
                error_message = NULL
            WHERE id IN (
                SELECT id 
                FROM outbox_events
                WHERE status = 'NEW'
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED 
            )
            RETURNING *
            """, nativeQuery = true)
    List<OutboxEventEntity> claimNewEvents(@Param("limit") int limit);
}
