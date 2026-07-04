package dev.azdanov.corebanking.account.application.queryhandler;

import dev.azdanov.corebanking.account.api.mapper.TransactionResponseMapper;
import dev.azdanov.corebanking.account.api.response.TransactionResponse;
import dev.azdanov.corebanking.account.application.query.GetTransactionsQuery;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetTransactionsHandler {
    private final AccountRepository accountRepository;

    public GetTransactionsHandler(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> handle(GetTransactionsQuery query) {
        var account = accountRepository.findByIdWithBalances(new AccountId(query.accountId()));
        return accountRepository.findTransactions(new AccountId(account.id().value())).stream()
            .map(TransactionResponseMapper::toResponse)
            .toList();
    }
}
