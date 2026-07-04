package dev.azdanov.corebanking.account.application.commandhandler;

import dev.azdanov.corebanking.account.api.response.BalanceResponse;
import dev.azdanov.corebanking.account.application.command.CreateAccountCommand;
import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.BalanceRepository;
import dev.azdanov.corebanking.shared.UuidFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAccountHandlerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OpenAccountHandler handler;

    @Test
    void shouldCreateAccountSuccessfully() {
        UUID customerId = UuidFactory.generate();
        String country = "US";
        List<String> currencies = List.of("USD", "EUR");
        var command = new CreateAccountCommand(customerId, country, currencies);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        doNothing().when(accountRepository).save(captor.capture());
        when(accountRepository.findByIdWithBalances(any()))
            .thenAnswer(_ -> captor.getValue());
        doNothing().when(balanceRepository).createInitialBalances(any(), any());

        var response = handler.createAccount(command);

        BigDecimal zeroAmount = new BigDecimal("0.00");

        assertThat(response.accountId()).isNotNull();
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.balances()).hasSize(2);

        assertThat(response.balances().stream()
            .filter(b -> b.currency().equals("USD")).findFirst())
            .isPresent()
            .map(BalanceResponse::availableAmount)
            .hasValue(zeroAmount);

        assertThat(response.balances().stream()
            .filter(b -> b.currency().equals("EUR")).findFirst())
            .isPresent()
            .map(BalanceResponse::availableAmount)
            .hasValue(zeroAmount);

        Account savedAccount = captor.getValue();
        assertThat(savedAccount.customerId().value()).isEqualTo(customerId);
        assertThat(savedAccount.country()).isEqualTo(country);
        assertThat(savedAccount.balances()).hasSize(2);
        assertThat(savedAccount.balances()).containsKeys("USD", "EUR");

        verify(balanceRepository).createInitialBalances(any(), any());
        verify(accountRepository).findByIdWithBalances(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void shouldCreateAccountWithSingleCurrency() {
        UUID customerId = UuidFactory.generate();
        String country = "GB";
        List<String> currencies = List.of("GBP");
        var command = new CreateAccountCommand(customerId, country, currencies);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        doNothing().when(accountRepository).save(captor.capture());
        when(accountRepository.findByIdWithBalances(any()))
            .thenAnswer(_ -> captor.getValue());
        doNothing().when(balanceRepository).createInitialBalances(any(), any());

        var response = handler.createAccount(command);

        assertThat(response.accountId()).isNotNull();
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.balances()).hasSize(1);
        assertThat(response.balances().getFirst().currency()).isEqualTo("GBP");
        assertThat(response.balances().getFirst().availableAmount()).isEqualTo(new BigDecimal("0.00"));

        Account savedAccount = captor.getValue();
        assertThat(savedAccount.customerId().value()).isEqualTo(customerId);
        assertThat(savedAccount.country()).isEqualTo(country);

        verify(balanceRepository).createInitialBalances(any(), any());
        verify(accountRepository).findByIdWithBalances(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void shouldCreateAccountWithMultipleCurrencies() {
        UUID customerId = UuidFactory.generate();
        String country = "DE";
        List<String> currencies = List.of("EUR", "USD", "GBP");
        var command = new CreateAccountCommand(customerId, country, currencies);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        doNothing().when(accountRepository).save(captor.capture());
        when(accountRepository.findByIdWithBalances(any()))
            .thenAnswer(_ -> captor.getValue());
        doNothing().when(balanceRepository).createInitialBalances(any(), any());

        var response = handler.createAccount(command);

        BigDecimal zeroAmount = new BigDecimal("0.00");

        assertThat(response.accountId()).isNotNull();
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.balances()).hasSize(3);

        response.balances().forEach(b ->
            assertThat(b.availableAmount()).isEqualTo(zeroAmount)
        );

        Account savedAccount = captor.getValue();
        assertThat(savedAccount.customerId().value()).isEqualTo(customerId);
        assertThat(savedAccount.country()).isEqualTo(country);
        assertThat(savedAccount.balances()).containsKeys("EUR", "USD", "GBP");

        verify(balanceRepository).createInitialBalances(any(), any());
        verify(accountRepository).findByIdWithBalances(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void shouldSaveAccountBeforeCreatingInitialBalances() {
        UUID customerId = UuidFactory.generate();
        String country = "US";
        List<String> currencies = List.of("USD");
        var command = new CreateAccountCommand(customerId, country, currencies);

        ArgumentCaptor<Account> saveCaptor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<AccountId> balanceAccountIdCaptor = ArgumentCaptor.forClass(AccountId.class);

        doNothing().when(accountRepository).save(saveCaptor.capture());
        when(accountRepository.findByIdWithBalances(any()))
            .thenAnswer(_ -> saveCaptor.getValue());

        handler.createAccount(command);

        verify(accountRepository).save(any());
        verify(balanceRepository).createInitialBalances(balanceAccountIdCaptor.capture(), any());
        assertThat(balanceAccountIdCaptor.getValue()).isEqualTo(saveCaptor.getValue().id());
        verify(eventPublisher).publishEvent(any(Object.class));
    }
}
