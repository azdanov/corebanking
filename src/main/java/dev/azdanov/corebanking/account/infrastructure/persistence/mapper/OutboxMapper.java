package dev.azdanov.corebanking.account.infrastructure.persistence.mapper;

import dev.azdanov.corebanking.account.infrastructure.persistence.model.OutboxEventRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OutboxMapper {

    @Insert("""
        INSERT INTO outbox_events (
            id,
            event_type,
            routing_key,
            payload,
            status,
            attempts,
            created_at
        )
        VALUES (
            #{id},
            #{eventType},
            #{routingKey},
            CAST(#{payload} AS jsonb),
            CAST('PENDING' AS outbox_event_status),
            0,
            #{createdAt}
        )
        """)
    void insert(
        @Param("id") UUID id,
        @Param("eventType") String eventType,
        @Param("routingKey") String routingKey,
        @Param("payload") String payload,
        @Param("createdAt") Instant createdAt
    );

    @Arg(column = "id", javaType = UUID.class)
    @Arg(column = "event_type", javaType = String.class)
    @Arg(column = "routing_key", javaType = String.class)
    @Arg(column = "payload", javaType = String.class)
    @Arg(column = "attempts", javaType = int.class)
    @Select("""
        SELECT id, event_type, routing_key, payload::text AS payload, attempts
        FROM outbox_events
        WHERE status IN (CAST('PENDING' AS outbox_event_status), CAST('FAILED' AS outbox_event_status))
          AND attempts < #{maxAttempts}
        ORDER BY created_at
        LIMIT #{limit}
        """)
    List<OutboxEventRow> findPendingBatch(
        @Param("limit") int limit,
        @Param("maxAttempts") int maxAttempts
    );

    @Update("""
        UPDATE outbox_events
        SET status = CAST('PROCESSING' AS outbox_event_status)
        WHERE id = #{id}
          AND status IN (CAST('PENDING' AS outbox_event_status), CAST('FAILED' AS outbox_event_status))
        """)
    int markProcessing(@Param("id") UUID id);

    @Update("""
        UPDATE outbox_events
        SET status = CAST('PROCESSED' AS outbox_event_status),
            processed_at = #{processedAt},
            last_error = NULL
        WHERE id = #{id}
        """)
    void markProcessed(
        @Param("id") UUID id,
        @Param("processedAt") Instant processedAt
    );

    @Update("""
        UPDATE outbox_events
        SET status = CAST('FAILED' AS outbox_event_status),
            attempts = attempts + 1,
            last_error = #{lastError}
        WHERE id = #{id}
        """)
    void markFailed(
        @Param("id") UUID id,
        @Param("lastError") String lastError
    );
}
