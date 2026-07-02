package dev.azdanov.corebanking.account.infrastructure.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record AccountRow(
    UUID id,
    UUID customerId,
    String country,
    Instant createdAt
) {
}
