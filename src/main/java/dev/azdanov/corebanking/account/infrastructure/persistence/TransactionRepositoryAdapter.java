package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.account.domain.account.Transaction;
import dev.azdanov.corebanking.account.domain.repository.TransactionRepository;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.BalanceMapper;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.TransactionMapper;
import dev.azdanov.corebanking.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class TransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionMapper transactionMapper;
    private final BalanceMapper balanceMapper;

    public TransactionRepositoryAdapter(
        TransactionMapper transactionMapper,
        BalanceMapper balanceMapper
    ) {
        this.transactionMapper = transactionMapper;
        this.balanceMapper = balanceMapper;
    }

    @Override
    public void post(Transaction transaction) {
        var updatedRows = switch (transaction.direction()) {
            case IN -> balanceMapper.incrementBalance(
                transaction.accountId().value(), transaction.currency(), transaction.amount().getAmount());
            case OUT -> balanceMapper.decrementBalanceIfEnough(
                transaction.accountId().value(), transaction.currency(), transaction.amount().getAmount());
        };
        if (updatedRows == 0) {
            throw new BusinessRuleException("Balance update rejected for account " + transaction.accountId());
        }

        transactionMapper.insert(
            transaction.id().value(),
            transaction.accountId().value(),
            transaction.amount().getAmount(),
            transaction.currency(),
            transaction.direction().name(),
            transaction.description(),
            transaction.balanceAfter().getAmount(),
            transaction.createdAt()
        );

    }
}
