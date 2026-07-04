package dev.azdanov.corebanking.account.infrastructure.messaging;

import dev.azdanov.corebanking.account.application.event.AccountCreatedEvent;
import dev.azdanov.corebanking.account.application.event.TransactionCreatedEvent;
import dev.azdanov.corebanking.account.domain.repository.OutboxRepository;
import dev.azdanov.corebanking.shared.exception.InfrastructureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxEventListener {

    private static final String ACCOUNT_CREATED_ROUTING_KEY = "account.created";
    private static final String TRANSACTION_CREATED_ROUTING_KEY = "account.transaction.created";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventListener(
        OutboxRepository outboxRepository,
        ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAccountCreated(AccountCreatedEvent event) {
        outboxRepository.save(
            event.getClass().getSimpleName(),
            ACCOUNT_CREATED_ROUTING_KEY,
            toJson(event),
            event.occurredAt()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTransactionCreated(TransactionCreatedEvent event) {
        outboxRepository.save(
            event.getClass().getSimpleName(),
            TRANSACTION_CREATED_ROUTING_KEY,
            toJson(event),
            event.occurredAt()
        );
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new InfrastructureException("Failed to serialize outbox payload", e);
        }
    }
}
