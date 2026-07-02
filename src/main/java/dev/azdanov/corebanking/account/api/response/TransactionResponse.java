package dev.azdanov.corebanking.account.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResponse(
    UUID accountId,
    UUID transactionId,
    BigDecimal amount,
    String currency,
    String direction,
    String description,
    BigDecimal balanceAfter
) {
}
