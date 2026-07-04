package dev.azdanov.corebanking.account.application.commandhandler;

import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.TransactionRepository;
import dev.azdanov.corebanking.shared.UuidFactory;
import dev.azdanov.corebanking.shared.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateTransactionHandlerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private CreateTransactionHandler handler;

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

        verify(accountRepository, never()).findByIdWithBalances(org.mockito.ArgumentMatchers.any());
        verify(transactionRepository, never()).post(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectInvalidDirection() {
        var command = new CreateTransactionCommand(
            UuidFactory.generate(),
            new BigDecimal("1.00"),
            "USD",
            "SIDEWAYS",
            "Deposit"
        );

        assertThatThrownBy(() -> handler.createTransaction(command))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Invalid direction: SIDEWAYS");

        verify(accountRepository, never()).findByIdWithBalances(org.mockito.ArgumentMatchers.any());
        verify(transactionRepository, never()).post(org.mockito.ArgumentMatchers.any());
    }
}
