package dev.azdanov.corebanking.account.api.mapper;

import dev.azdanov.corebanking.account.api.response.TransactionResponse;
import dev.azdanov.corebanking.account.domain.account.Transaction;

public final class TransactionResponseMapper {

    private TransactionResponseMapper() {
    }

    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
            transaction.accountId().value(),
            transaction.id().value(),
            transaction.amount().getAmount(),
            transaction.currency(),
            transaction.direction().name(),
            transaction.description(),
            transaction.balanceAfter().getAmount());
    }
}
