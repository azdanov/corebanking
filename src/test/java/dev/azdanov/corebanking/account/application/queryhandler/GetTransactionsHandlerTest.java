package dev.azdanov.corebanking.account.application.queryhandler;

import dev.azdanov.corebanking.account.application.query.GetTransactionsQuery;
import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.Transaction;
import dev.azdanov.corebanking.account.domain.account.TransactionDirection;
import dev.azdanov.corebanking.account.domain.account.TransactionId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.shared.UuidFactory;
import dev.azdanov.corebanking.shared.exception.NotFoundException;
import dev.azdanov.corebanking.shared.money.MoneyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetTransactionsHandlerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-07T10:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private GetTransactionsHandler handler;

    @Test
    void shouldThrowNotFoundWhenAccountDoesNotExist() {
        var accountId = UuidFactory.generate();
        var query = new GetTransactionsQuery(accountId);
        given(accountRepository.findByIdWithBalances(any(AccountId.class)))
            .willThrow(new NotFoundException("Account not found: " + accountId));

        assertThatThrownBy(() -> handler.handle(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Account not found");

        verify(accountRepository, never()).findTransactions(any(AccountId.class));
    }

    @Test
    void shouldReturnTransactionsWhenAccountExists() {
        var accountId = UuidFactory.generate();
        var transaction = new Transaction(
            new TransactionId(UuidFactory.generate()),
            new AccountId(accountId),
            MoneyFactory.of("USD", new BigDecimal("25.00")),
            "USD",
            TransactionDirection.IN,
            "Deposit",
            MoneyFactory.of("USD", new BigDecimal("125.00")),
            FIXED_TIME
        );

        var account = mock(Account.class);
        given(account.id()).willReturn(new AccountId(accountId));

        given(accountRepository.findByIdWithBalances(any(AccountId.class))).willReturn(account);
        given(accountRepository.findTransactions(any(AccountId.class))).willReturn(List.of(transaction));

        var response = handler.handle(new GetTransactionsQuery(accountId));

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().accountId()).isEqualTo(accountId);
        assertThat(response.getFirst().direction().name()).isEqualTo("IN");
    }
}
