package dev.azdanov.corebanking.account.application.commandhandler;

import dev.azdanov.corebanking.account.api.mapper.AccountResponseMapper;
import dev.azdanov.corebanking.account.api.response.AccountResponse;
import dev.azdanov.corebanking.account.application.command.CreateAccountCommand;
import dev.azdanov.corebanking.account.application.event.AccountCreatedEvent;
import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.CustomerId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.BalanceRepository;
import dev.azdanov.corebanking.shared.money.CurrencyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OpenAccountHandler {
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OpenAccountHandler(
        AccountRepository accountRepository,
        BalanceRepository balanceRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountCommand command) {
        var currencies = getValidatedCurrencies(command);

        var accountId = AccountId.create();
        var customerId = new CustomerId(command.customerId());
        var account = new Account(accountId, customerId, command.country(), currencies, Instant.now());

        accountRepository.save(account);
        balanceRepository.createInitialBalances(accountId, currencies);

        eventPublisher.publishEvent(new AccountCreatedEvent(
            account.id().value(),
            account.customerId().value(),
            account.country(),
            currencies,
            Instant.now()
        ));

        return AccountResponseMapper.toResponse(accountRepository.findByIdWithBalances(accountId));
    }

    private static List<String> getValidatedCurrencies(CreateAccountCommand command) {
        return command.currencies().stream()
            .map(currency -> CurrencyFactory.of(currency).getCode())
            .distinct()
            .toList();
    }
}
