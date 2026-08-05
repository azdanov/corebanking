package dev.azdanov.corebanking.account.application.commandhandler;

import dev.azdanov.corebanking.account.api.model.TransactionDirection;
import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;
import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.CustomerId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.TransactionRepository;
import dev.azdanov.corebanking.shared.UuidFactory;
import dev.azdanov.corebanking.shared.exception.BusinessRuleException;
import dev.azdanov.corebanking.shared.exception.InvalidInputException;
import dev.azdanov.corebanking.shared.money.MoneyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTransactionHandlerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-07T10:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CreateTransactionHandler handler;

    @Test
    void shouldRejectNullDirection() {
        var command = new CreateTransactionCommand(
            UuidFactory.generate(),
            new BigDecimal("1.00"),
            "USD",
            null,
            "Deposit"
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Invalid direction: value is required");

        verify(accountRepository, never()).findByIdWithBalancesForUpdate(any());
        verify(transactionRepository, never()).post(any());
    }

    @Test
    void shouldRejectBlankDirection() {
        var command = new CreateTransactionCommand(
            UuidFactory.generate(),
            new BigDecimal("1.00"),
            "USD",
            "",
            "Deposit"
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Invalid direction: value is required");

        verify(accountRepository, never()).findByIdWithBalancesForUpdate(any());
        verify(transactionRepository, never()).post(any());
    }

    @Test
    void shouldRejectNullDescription() {
        var command = new CreateTransactionCommand(
            UuidFactory.generate(),
            new BigDecimal("1.00"),
            "USD",
            "IN",
            null
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Description missing");

        verify(accountRepository, never()).findByIdWithBalancesForUpdate(any());
        verify(transactionRepository, never()).post(any());
    }

    @Test
    void shouldRejectBlankDescription() {
        var command = new CreateTransactionCommand(
            UuidFactory.generate(),
            new BigDecimal("1.00"),
            "USD",
            "IN",
            "  "
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Description missing");

        verify(accountRepository, never()).findByIdWithBalancesForUpdate(any());
        verify(transactionRepository, never()).post(any());
    }

    @Test
    void shouldRejectNegativeAmount() {
        var command = new CreateTransactionCommand(
            UuidFactory.generate(),
            new BigDecimal("-1.00"),
            "USD",
            "IN",
            "Deposit"
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Invalid amount: value cannot be negative");

        verify(accountRepository, never()).findByIdWithBalancesForUpdate(any());
        verify(transactionRepository, never()).post(any());
    }

    @Test
    void shouldRejectInvalidDirectionEnum() {
        var command = new CreateTransactionCommand(
            UuidFactory.generate(),
            new BigDecimal("1.00"),
            "USD",
            "SIDEWAYS",
            "Deposit"
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Invalid direction: SIDEWAYS")
            .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(accountRepository, never()).findByIdWithBalancesForUpdate(any());
        verify(transactionRepository, never()).post(any());
    }

    private Account createAccountWithBalance(BigDecimal initialBalance) {
        AccountId accountId = AccountId.create();
        CustomerId customerId = CustomerId.create();
        Account account = new Account(accountId, customerId, "US", List.of("USD"), FIXED_TIME);
        account.balances().get("USD").setAvailableAmount(MoneyFactory.of("USD", initialBalance));
        return account;
    }

    @Test
    void shouldSuccessfullyCreateInTransaction() {
        Account account = createAccountWithBalance(new BigDecimal("500.00"));
        AccountId accountId = account.id();

        when(accountRepository.findByIdWithBalancesForUpdate(accountId))
            .thenReturn(account);

        var command = new CreateTransactionCommand(
            accountId.value(),
            new BigDecimal("100.00"),
            "USD",
            "IN",
            "Salary deposit"
        );

        var response = handler.createTransaction(command);

        assertThat(response.amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.direction()).isEqualTo(TransactionDirection.IN);
        assertThat(response.description()).isEqualTo("Salary deposit");
        assertThat(response.balanceAfter()).isEqualTo(new BigDecimal("600.00"));
        assertThat(response.accountId()).isEqualTo(accountId.value());

        verify(transactionRepository).post(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void shouldSuccessfullyCreateOutTransaction() {
        Account account = createAccountWithBalance(new BigDecimal("500.00"));
        AccountId accountId = account.id();

        when(accountRepository.findByIdWithBalancesForUpdate(accountId))
            .thenReturn(account);

        var command = new CreateTransactionCommand(
            accountId.value(),
            new BigDecimal("150.00"),
            "USD",
            "OUT",
            "Bill payment"
        );

        var response = handler.createTransaction(command);

        assertThat(response.amount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(response.description()).isEqualTo("Bill payment");
        assertThat(response.balanceAfter()).isEqualTo(new BigDecimal("350.00"));

        verify(transactionRepository).post(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void shouldRejectOutTransactionWhenInsufficientFunds() {
        Account account = createAccountWithBalance(new BigDecimal("500.00"));

        when(accountRepository.findByIdWithBalancesForUpdate(any()))
            .thenReturn(account);

        var command = new CreateTransactionCommand(
            account.id().value(),
            new BigDecimal("1000.00"),
            "USD",
            "OUT",
            "Oversized withdrawal"
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("Insufficient funds");

        verify(transactionRepository, never()).post(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void shouldPropagateAccountNotFoundException() {
        when(accountRepository.findByIdWithBalancesForUpdate(any()))
            .thenThrow(new RuntimeException("Account not found"));

        var command = new CreateTransactionCommand(
            UuidFactory.generate(),
            new BigDecimal("100.00"),
            "USD",
            "IN",
            "Deposit"
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Account not found");

        verify(transactionRepository, never()).post(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
