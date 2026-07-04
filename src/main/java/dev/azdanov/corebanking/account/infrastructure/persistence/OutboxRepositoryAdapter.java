package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.account.domain.repository.OutboxRepository;
import dev.azdanov.corebanking.account.domain.repository.PendingOutboxEvent;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.OutboxMapper;
import dev.azdanov.corebanking.shared.UuidFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxRepositoryAdapter implements OutboxRepository {

    private final OutboxMapper outboxMapper;

    public OutboxRepositoryAdapter(OutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String eventType, String routingKey, String payload, Instant createdAt) {
        outboxMapper.insert(UuidFactory.generate(), eventType, routingKey, payload, createdAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<PendingOutboxEvent> claimPendingBatch(int limit, int maxAttempts) {
        var rows = outboxMapper.findPendingBatch(limit, maxAttempts);
        var claimed = new ArrayList<PendingOutboxEvent>(rows.size());

        for (var row : rows) {
            if (outboxMapper.markProcessing(row.id()) == 1) {
                claimed.add(new PendingOutboxEvent(
                    row.id(),
                    row.eventType(),
                    row.routingKey(),
                    row.payload(),
                    row.attempts()
                ));
            }
        }

        return claimed;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID id, Instant processedAt) {
        outboxMapper.markProcessed(id, processedAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, String lastError) {
        outboxMapper.markFailed(id, lastError);
    }
}
