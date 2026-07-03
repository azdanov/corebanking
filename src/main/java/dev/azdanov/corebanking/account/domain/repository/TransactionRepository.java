package dev.azdanov.corebanking.account.domain.repository;

import dev.azdanov.corebanking.account.domain.account.Transaction;

public interface TransactionRepository {
    void post(Transaction transaction);

}
