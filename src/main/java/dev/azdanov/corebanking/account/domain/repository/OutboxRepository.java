package dev.azdanov.corebanking.account.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {
    void save(String eventType, String routingKey, String payload, Instant createdAt);

    List<PendingOutboxEvent> claimPendingBatch(int limit, int maxAttempts);

    void markProcessed(UUID id, Instant processedAt);

    void markFailed(UUID id, String lastError);
}
