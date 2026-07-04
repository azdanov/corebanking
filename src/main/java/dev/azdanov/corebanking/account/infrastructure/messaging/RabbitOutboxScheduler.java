package dev.azdanov.corebanking.account.infrastructure.messaging;

import dev.azdanov.corebanking.account.domain.repository.OutboxRepository;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Component
public class RabbitOutboxScheduler {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final int batchSize;
    private final int maxAttempts;

    public RabbitOutboxScheduler(
        OutboxRepository outboxRepository,
        RabbitTemplate rabbitTemplate,
        @Value("${corebanking.messaging.exchange}") String exchangeName,
        @Value("${corebanking.outbox.relay.batch-size:100}") int batchSize,
        @Value("${corebanking.outbox.relay.max-attempts:10}") int maxAttempts
    ) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${corebanking.outbox.relay.fixed-delay:1000}")
    public void processPendingEvents() {
        var pending = outboxRepository.claimPendingBatch(batchSize, maxAttempts);
        for (var message : pending) {
            try {
                rabbitTemplate.convertAndSend(exchangeName, message.routingKey(), message.payload());
                outboxRepository.markProcessed(message.id(), Instant.now());
            } catch (AmqpException e) {
                outboxRepository.markFailed(message.id(), trimError(e.getMessage()));
            }
        }
    }

    private String trimError(String error) {
        if (!StringUtils.hasText(error)) {
            return "Unknown relay error";
        }
        if (error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
