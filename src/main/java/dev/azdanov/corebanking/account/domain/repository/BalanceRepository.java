package dev.azdanov.corebanking.account.domain.repository;

import dev.azdanov.corebanking.account.domain.account.AccountId;

import java.util.List;

public interface BalanceRepository {
    void createInitialBalances(AccountId accountId, List<String> currencies);

}
