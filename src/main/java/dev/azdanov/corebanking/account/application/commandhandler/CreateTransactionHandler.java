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
        CreateTransactionCommand command
    ) {
        var account = accountRepository.findByIdWithBalances(new AccountId(command.accountId()));

        var direction = TransactionDirection.valueOf(command.direction());
        var transaction = account.post(
            direction,
            command.currency(),
            MoneyFactory.of(command.currency(), command.amount()),
            command.description(),
            Instant.now()
        );

        transactionRepository.post(transaction);
        return TransactionResponseMapper.toResponse(transaction);
    }
}
