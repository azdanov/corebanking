package dev.azdanov.corebanking.account.application.commandhandler;

import dev.azdanov.corebanking.account.api.mapper.AccountResponseMapper;
import dev.azdanov.corebanking.account.api.response.AccountResponse;
import dev.azdanov.corebanking.account.application.command.CreateAccountCommand;
import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.CustomerId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.BalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
public class OpenAccountHandler {
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;

    public OpenAccountHandler(
        AccountRepository accountRepository,
        BalanceRepository balanceRepository
    ) {
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountCommand command) {
        var accountId = AccountId.generate();
        var customerId = new CustomerId(command.customerId());
        var account = new Account(accountId, customerId, command.country(), Set.copyOf(command.currencies()), Instant.now());

        accountRepository.save(account);
        balanceRepository.createInitialBalances(accountId, command.currencies());
        return AccountResponseMapper.toResponse(accountRepository.findByIdWithBalances(accountId));
    }
}
