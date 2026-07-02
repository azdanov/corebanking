package dev.azdanov.corebanking.account.application.commandhandler;

import dev.azdanov.corebanking.account.api.mapper.TransactionResponseMapper;
import dev.azdanov.corebanking.account.api.response.TransactionResponse;
import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.TransactionDirection;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.TransactionRepository;
import dev.azdanov.corebanking.shared.money.MoneyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CreateTransactionHandler {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public CreateTransactionHandler(
        AccountRepository accountRepository,
        TransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse createTransaction(
        UUID accountId,
        CreateTransactionCommand createTransaction
    ) {
        var account = accountRepository.findByIdWithBalances(new AccountId(accountId));

        var direction = TransactionDirection.valueOf(createTransaction.direction());
        var transaction = account.post(
            direction,
            createTransaction.currency(),
            MoneyFactory.of(createTransaction.currency(), createTransaction.amount()),
            createTransaction.description(),
            Instant.now()
        );

        transactionRepository.post(accountId, createTransaction);
        return TransactionResponseMapper.toResponse(transaction);
    }
}
