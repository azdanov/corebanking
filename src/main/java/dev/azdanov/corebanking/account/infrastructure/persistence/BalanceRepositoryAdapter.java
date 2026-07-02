package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.repository.BalanceRepository;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.BalanceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BalanceRepositoryAdapter implements BalanceRepository {
    private final BalanceMapper balanceMapper;

    public BalanceRepositoryAdapter(BalanceMapper balanceMapper) {
        this.balanceMapper = balanceMapper;
    }

    @Override
    public void createInitialBalances(AccountId accountId, List<String> currencies) {
        balanceMapper.insertInitialBalances(accountId.value(), currencies);
    }
}
