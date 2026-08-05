package dev.azdanov.corebanking.account.application.commandhandler;

import dev.azdanov.corebanking.account.api.mapper.TransactionResponseMapper;
import dev.azdanov.corebanking.account.api.response.TransactionResponse;
import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;
import dev.azdanov.corebanking.account.application.event.TransactionCreatedEvent;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.TransactionDirection;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.TransactionRepository;
import dev.azdanov.corebanking.shared.exception.InvalidInputException;
import dev.azdanov.corebanking.shared.money.MoneyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CreateTransactionHandler {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CreateTransactionHandler(
        AccountRepository accountRepository,
        TransactionRepository transactionRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TransactionResponse createTransaction(
        CreateTransactionCommand command
    ) {
        if (command.direction() == null || command.direction().isBlank()) {
            throw new InvalidInputException("Invalid direction: value is required");
        }
        if (command.description() == null || command.description().isBlank()) {
            throw new InvalidInputException("Description missing");
        }
        if (command.amount() != null && command.amount().signum() < 0) {
            throw new InvalidInputException("Invalid amount: value cannot be negative");
        }

        TransactionDirection direction;
        try {
            direction = TransactionDirection.valueOf(command.direction());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid direction: " + command.direction(), e);
        }

        var account = accountRepository.findByIdWithBalancesForUpdate(new AccountId(command.accountId()));

        var transaction = account.post(
            direction,
            command.currency(),
            MoneyFactory.of(command.currency(), command.amount()),
            command.description(),
            Instant.now()
        );

        transactionRepository.post(transaction);
        eventPublisher.publishEvent(new TransactionCreatedEvent(
            transaction.id().value(),
            transaction.accountId().value(),
            transaction.amount().getAmount(),
            transaction.currency(),
            transaction.direction().name(),
            transaction.description(),
            transaction.balanceAfter().getAmount(),
            transaction.createdAt()
        ));

        return TransactionResponseMapper.toResponse(transaction);
    }
}
