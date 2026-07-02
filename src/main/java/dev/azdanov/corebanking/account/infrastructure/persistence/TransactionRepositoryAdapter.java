package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.Transaction;
import dev.azdanov.corebanking.account.domain.account.TransactionDirection;
import dev.azdanov.corebanking.account.domain.account.TransactionId;
import dev.azdanov.corebanking.account.domain.repository.TransactionRepository;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.BalanceMapper;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.TransactionMapper;
import dev.azdanov.corebanking.shared.money.MoneyFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class TransactionRepositoryAdapter implements TransactionRepository {
    private final TransactionMapper transactionMapper;
    private final BalanceMapper balanceMapper;

    public TransactionRepositoryAdapter(
        TransactionMapper transactionMapper,
        BalanceMapper balanceMapper
    ) {
        this.transactionMapper = transactionMapper;
        this.balanceMapper = balanceMapper;
    }

    @Override
    public void post(UUID accountId, CreateTransactionCommand command) {
        var direction = TransactionDirection.valueOf(command.direction());
        var updatedRows = switch (direction) {
            case IN -> balanceMapper.incrementBalance(accountId, command.currency(), command.amount());
            case OUT -> balanceMapper.decrementBalanceIfEnough(accountId, command.currency(), command.amount());
        };
        if (updatedRows == 0) {
            throw new IllegalStateException("Balance update rejected for account " + accountId);
        }

        var balanceAfter = balanceMapper.findAvailableAmount(accountId, command.currency());
        if (balanceAfter == null) {
            throw new IllegalStateException("Balance missing for account " + accountId + " and currency " + command.currency());
        }

        var now = Instant.now();
        var transactionId = UUID.ofEpochMillis(System.currentTimeMillis());

        transactionMapper.insert(
            transactionId,
            accountId,
            command.amount(),
            command.currency(),
            direction.name(),
            command.description(),
            balanceAfter,
            now,
            now
        );

        new Transaction(
            new TransactionId(transactionId),
            new AccountId(accountId),
            MoneyFactory.of(command.currency(), command.amount()),
            command.currency(),
            direction,
            command.description(),
            MoneyFactory.of(command.currency(), balanceAfter),
            now,
            now
        );
    }

}
