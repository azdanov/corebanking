package dev.azdanov.corebanking.account.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountCreatedEvent(
    UUID accountId,
    UUID customerId,
    String country,
    List<String> currencies,
    Instant occurredAt
) {
}
