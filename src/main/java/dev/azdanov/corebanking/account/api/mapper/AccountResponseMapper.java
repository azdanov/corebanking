package dev.azdanov.corebanking.account.api.mapper;

import dev.azdanov.corebanking.account.api.response.AccountResponse;
import dev.azdanov.corebanking.account.api.response.BalanceResponse;
import dev.azdanov.corebanking.account.domain.account.Account;

import java.util.List;

public final class AccountResponseMapper {

    private AccountResponseMapper() {
    }

    public static AccountResponse toResponse(Account account) {
        List<BalanceResponse> balanceResponses =
            account.balances().values().stream()
                .map(
                    balance ->
                        new BalanceResponse(balance.currency(), balance.availableAmount().getAmount()))
                .toList();

        return new AccountResponse(
            account.id().value(), account.customerId().value(), account.country(), balanceResponses);
    }
}
