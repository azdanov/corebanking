package dev.azdanov.corebanking.account.infrastructure.persistence.model;

import java.util.UUID;

public record OutboxEventRow(
    UUID id,
    String eventType,
    String routingKey,
    String payload,
    int attempts
) {
}
