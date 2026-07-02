package dev.azdanov.corebanking.account.application.query;

import java.util.UUID;

public record GetTransactionsQuery(UUID accountId) {
}
