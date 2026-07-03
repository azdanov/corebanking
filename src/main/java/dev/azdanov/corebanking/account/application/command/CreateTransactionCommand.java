package dev.azdanov.corebanking.account.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransactionCommand(
    UUID accountId,
    BigDecimal amount,
    String currency,
    String direction,
    String description
) {
}
