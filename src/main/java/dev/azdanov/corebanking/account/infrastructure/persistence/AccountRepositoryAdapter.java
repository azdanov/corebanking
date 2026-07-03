package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.CustomerId;
import dev.azdanov.corebanking.account.domain.account.Transaction;
import dev.azdanov.corebanking.account.domain.account.TransactionDirection;
import dev.azdanov.corebanking.account.domain.account.TransactionId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.AccountMapper;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.TransactionMapper;
import dev.azdanov.corebanking.shared.money.MoneyFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AccountRepositoryAdapter implements AccountRepository {
    private final AccountMapper accountMapper;
    private final TransactionMapper transactionMapper;

    public AccountRepositoryAdapter(
        AccountMapper accountMapper,
        TransactionMapper transactionMapper
    ) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
    }

    @Override
    public void save(Account account) {
        accountMapper.insert(
            account.id().value(),
            account.customerId().value(),
            account.country(),
            account.createdAt(),
            account.updatedAt()
        );
    }

    @Override
    public Account findByIdWithBalances(AccountId accountId) {
        var accountRow = accountMapper.findById(accountId.value());
        if (accountRow == null) {
            throw new IllegalArgumentException("Account not found: " + accountId.value());
        }

        var balances = accountMapper.findBalancesByAccountId(accountId.value());
        var currencies = new LinkedHashSet<String>();
        for (var balance : balances) {
            currencies.add(balance.currency());
        }

        var account = new Account(
            new AccountId(accountRow.id()),
            new CustomerId(accountRow.customerId()),
            accountRow.country(),
            currencies,
            accountRow.createdAt()
        );

        for (var balance : balances) {
            account.balance(balance.currency()).setAvailableAmount(
                MoneyFactory.of(balance.currency(), balance.availableAmount())
            );
        }

        return account;
    }

    @Override
    public List<Transaction> findTransactions(AccountId accountId) {
        return transactionMapper.findByAccountId(accountId.value()).stream()
            .map(row -> new Transaction(
                new TransactionId(row.id()),
                new AccountId(row.accountId()),
                MoneyFactory.of(row.currency(), row.amount()),
                row.currency(),
                TransactionDirection.valueOf(row.direction()),
                row.description(),
                MoneyFactory.of(row.currency(), row.balanceAfter()),
                row.createdAt()
            ))
            .toList();
    }
}
