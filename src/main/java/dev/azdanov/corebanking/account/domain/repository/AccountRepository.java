package dev.azdanov.corebanking.account.domain.repository;

import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.Transaction;

import java.util.List;

public interface AccountRepository {
    void save(Account account);

    Account findByIdWithBalances(AccountId accountId);

    /**
     * Loads the account and locks its balance rows for the current transaction,
     * serializing concurrent writes so balance computations never see stale reads.
     */
    Account findByIdWithBalancesForUpdate(AccountId accountId);

    List<Transaction> findTransactions(AccountId accountId);
}
