package dev.azdanov.corebanking.account.application.command;

import java.math.BigDecimal;

public record CreateTransactionCommand(
    BigDecimal amount,
    String currency,
    String direction,
    String description
) {
}
