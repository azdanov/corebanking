package dev.azdanov.corebanking.account.domain.repository;

import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;

import java.util.UUID;

public interface TransactionRepository {
    void post(UUID accountId, CreateTransactionCommand command);

}
