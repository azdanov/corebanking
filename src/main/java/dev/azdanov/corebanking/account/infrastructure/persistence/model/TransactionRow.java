package dev.azdanov.corebanking.account.infrastructure.persistence.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionRow(
    UUID id,
    UUID accountId,
    BigDecimal amount,
    String currency,
    String direction,
    String description,
    BigDecimal balanceAfter,
    Instant createdAt
) {
}
