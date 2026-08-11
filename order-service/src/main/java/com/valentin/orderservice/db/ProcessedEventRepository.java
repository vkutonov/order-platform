package com.valentin.orderservice.db;

import com.valentin.orderservice.domain.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {

    @Modifying
    @Query(value = """
           INSERT INTO ProcessedEventEntity (
                      eventId,
                      eventType,
                      aggregateId,
                      processedAt
           ) VALUES (
                      :eventId,
                      :eventType,
                      :aggregateId,
                      :processedAt
           )
           ON CONFLICT DO NOTHING
           """
    )
    int tryInsert(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("aggregateId") UUID aggregateId,
            @Param("processedAt") Instant processedAt
    );
}
