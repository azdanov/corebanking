package dev.azdanov.corebanking.account.domain.repository;

import java.util.UUID;

public record PendingOutboxEvent(
    UUID id,
    String eventType,
    String routingKey,
    String payload,
    int attempts
) {
}
