package dev.azdanov.corebanking.account.api.response;

import java.util.List;
import java.util.UUID;

public record AccountResponse(
    UUID accountId,
    UUID customerId,
    List<BalanceResponse> balances
) {
}
