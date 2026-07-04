package dev.azdanov.corebanking.account.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCreatedEvent(
    UUID transactionId,
    UUID accountId,
    BigDecimal amount,
    String currency,
    String direction,
    String description,
    BigDecimal balanceAfter,
    Instant occurredAt
) {
}
