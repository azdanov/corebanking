package dev.azdanov.corebanking.account.api.response;


import dev.azdanov.corebanking.account.api.model.TransactionDirection;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResponse(
    UUID accountId,
    UUID transactionId,
    BigDecimal amount,
    String currency,
    TransactionDirection direction,
    String description,
    BigDecimal balanceAfter
) {
}
