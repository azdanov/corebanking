package dev.azdanov.corebanking.account.application.queryhandler;

import dev.azdanov.corebanking.account.api.mapper.AccountResponseMapper;
import dev.azdanov.corebanking.account.api.response.AccountResponse;
import dev.azdanov.corebanking.account.application.query.GetAccountQuery;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAccountHandler {
    private final AccountRepository accountRepository;

    public GetAccountHandler(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public AccountResponse handle(GetAccountQuery query) {
        var account = accountRepository.findByIdWithBalances(new AccountId(query.accountId()));
        return AccountResponseMapper.toResponse(account);
    }
}
