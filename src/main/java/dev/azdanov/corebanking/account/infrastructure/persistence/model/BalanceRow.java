package dev.azdanov.corebanking.account.infrastructure.persistence.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceRow(
    UUID accountId,
    String currency,
    BigDecimal availableAmount
) {
}
