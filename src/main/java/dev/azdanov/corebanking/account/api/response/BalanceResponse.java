package dev.azdanov.corebanking.account.api.response;

import java.math.BigDecimal;

public record BalanceResponse(
    String currency,
    BigDecimal availableAmount
) {
}
