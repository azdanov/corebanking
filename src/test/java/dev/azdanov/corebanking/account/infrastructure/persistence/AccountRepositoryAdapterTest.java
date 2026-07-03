package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.TestcontainersConfiguration;
import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.CustomerId;
import dev.azdanov.corebanking.account.domain.account.TransactionDirection;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.BalanceRepository;
import dev.azdanov.corebanking.account.domain.repository.TransactionRepository;
import dev.azdanov.corebanking.shared.money.MoneyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("AccountRepositoryAdapter - persistence tests")
class AccountRepositoryAdapterTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-07T10:00:00Z");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private AccountId accountId;
    private CustomerId customerId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = AccountId.create();
        customerId = CustomerId.create();
        account = createAccount(Set.of("USD", "EUR"));
        accountRepository.save(account);
        balanceRepository.createInitialBalances(accountId, List.of("USD", "EUR"));
    }

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("should persist an account to the database")
        void shouldPersistAccount() {
            var found = accountRepository.findByIdWithBalances(accountId);

            assertThat(found).isNotNull();
            assertThat(found.id().value()).isEqualTo(accountId.value());
            assertThat(found.customerId().value()).isEqualTo(customerId.value());
            assertThat(found.country()).isEqualTo("US");
            assertThat(found.createdAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByIdWithBalances")
    class FindByIdWithBalancesTests {

        @Test
        @DisplayName("should load account with all balances at zero")
        void shouldLoadAccountWithBalances() {
            var found = accountRepository.findByIdWithBalances(accountId);

            assertThat(found).isNotNull();
            assertThat(found.id().value()).isEqualTo(accountId.value());
            assertThat(found.customerId().value()).isEqualTo(customerId.value());
            assertThat(found.country()).isEqualTo("US");
            assertThat(found.balances()).hasSize(2);
            assertThat(found.balances()).containsKeys("USD", "EUR");
            assertThat(found.balance("USD").availableAmount().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(found.balance("EUR").availableAmount().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName(
            "should return updated balances after transactions are persisted"
        )
        void shouldReflectTransactionChanges() {
            var deposit = MoneyFactory.of("USD", new BigDecimal("500.00"));
            var depositTxn = account.post(TransactionDirection.IN, "USD", deposit, "Deposit", FIXED_TIME);
            transactionRepository.post(depositTxn);

            var refreshed = accountRepository.findByIdWithBalances(accountId);
            var withdrawal = MoneyFactory.of("USD", new BigDecimal("100.00"));
            var withdrawalTxn = refreshed.post(TransactionDirection.OUT, "USD", withdrawal, "Withdrawal", FIXED_TIME);
            transactionRepository.post(withdrawalTxn);

            var found = accountRepository.findByIdWithBalances(accountId);

            assertThat(found.balance("USD").availableAmount().getAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
        }

        @Test
        @DisplayName("should throw when account does not exist")
        void shouldThrowWhenAccountNotFound() {
            var missingAccountId = AccountId.create();

            assertThatThrownBy(() -> accountRepository.findByIdWithBalances(missingAccountId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
        }

        @Test
        @DisplayName(
            "should return account with empty balances when none seeded"
        )
        void shouldReturnAccountWithoutInitialBalances() {
            var orphanId = AccountId.create();
            var orphanCustomer = CustomerId.create();
            var orphan = new Account(orphanId, orphanCustomer, "GB", Set.of(), FIXED_TIME);
            accountRepository.save(orphan);

            var found = accountRepository.findByIdWithBalances(orphanId);

            assertThat(found).isNotNull();
            assertThat(found.balances()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findTransactions")
    class FindTransactionsTests {

        @Test
        @DisplayName(
            "should return transactions for a given account ordered by created_at DESC"
        )
        void shouldReturnTransactions() {
            var first = MoneyFactory.of("USD", new BigDecimal("100.00"));
            var t1 = FIXED_TIME;
            var txn1 = account.post(TransactionDirection.IN, "USD", first, "First", t1);
            transactionRepository.post(txn1);

            var second = MoneyFactory.of("USD", new BigDecimal("50.00"));
            var t2 = t1.plusSeconds(1);
            var txn2 = account.post(TransactionDirection.IN, "USD", second, "Second", t2);
            transactionRepository.post(txn2);

            var transactions = accountRepository.findTransactions(accountId);

            assertThat(transactions).hasSize(2);
            assertThat(transactions.get(0).id().value()).isEqualTo(txn2.id().value());
            assertThat(transactions.get(1).id().value()).isEqualTo(txn1.id().value());
        }

        @Test
        @DisplayName(
            "should return empty list for account with no transactions"
        )
        void shouldReturnEmptyListForAccountWithNoTransactions() {
            var transactions = accountRepository.findTransactions(accountId);

            assertThat(transactions).isEmpty();
        }

        @Test
        @DisplayName("should return correct transaction details")
        void shouldReturnCorrectTransactionDetails() {
            var deposit = MoneyFactory.of("USD", new BigDecimal("1000.00"));
            var txn = account.post(TransactionDirection.IN, "USD", deposit, "Test description", FIXED_TIME);
            transactionRepository.post(txn);

            var transactions = accountRepository.findTransactions(accountId);
            var found = transactions.getFirst();
            assertThat(found.id().value()).isEqualTo(txn.id().value());
            assertThat(found.accountId().value()).isEqualTo(accountId.value());
            assertThat(found.amount().getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(found.currency()).isEqualTo("USD");
            assertThat(found.direction()).isEqualTo(TransactionDirection.IN);
            assertThat(found.description()).isEqualTo("Test description");
            assertThat(found.balanceAfter().getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(found.createdAt()).isNotNull();
        }
    }

    private Account createAccount(Set<String> currencies) {
        return new Account(accountId, customerId, "US", currencies, FIXED_TIME);
    }
}
