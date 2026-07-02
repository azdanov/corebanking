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
        return accountRepository.findTransactions(new AccountId(query.accountId())).stream()
            .map(TransactionResponseMapper::toResponse)
            .toList();
    }
}
