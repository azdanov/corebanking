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
class TransactionRepositoryAdapterTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-07T10:00:00Z");
    private static final String COUNTRY = "US";

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    private AccountId accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = AccountId.create();
        var customerId = CustomerId.create();
        account = createAccount(customerId, Set.of("USD", "EUR"));
        accountRepository.save(account);
        balanceRepository.createInitialBalances(accountId, List.of("USD", "EUR"));
    }

    private void seedBalance() {
        var seedTime = FIXED_TIME.minusSeconds(3600);
        var deposit = MoneyFactory.of("USD", new BigDecimal("1000.00"));
        var depositTxn = account.post(TransactionDirection.IN, "USD", deposit, "Seed", seedTime);
        transactionRepository.post(depositTxn);
        account = accountRepository.findByIdWithBalances(accountId);
    }

    @Nested
    class PostingTransactionInDirectionTests {

        @Test
        void shouldDepositMoneyAndUpdateAccountBalance() {
            var depositAmount = MoneyFactory.of("USD", new BigDecimal("1000.00"));
            var transaction = account.post(TransactionDirection.IN, "USD", depositAmount, "Initial deposit", FIXED_TIME);

            transactionRepository.post(transaction);

            var updated = accountRepository.findByIdWithBalances(accountId);
            assertThat(updated.balance("USD").availableAmount().getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));

            var transactions = accountRepository.findTransactions(accountId);
            assertThat(transactions).hasSize(1);
            assertThat(transactions.getFirst().id().value()).isEqualTo(transaction.id().value());
            assertThat(transactions.getFirst().amount().getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(transactions.getFirst().currency()).isEqualTo("USD");
            assertThat(transactions.getFirst().direction()).isEqualTo(TransactionDirection.IN);
            assertThat(transactions.getFirst().description()).isEqualTo("Initial deposit");
            assertThat(transactions.getFirst().balanceAfter().getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        }

        @Test
        void shouldAccumulateMultipleDepositsOnSameBalance() {
            seedBalance();

            var first = MoneyFactory.of("USD", new BigDecimal("500.00"));
            var second = MoneyFactory.of("USD", new BigDecimal("250.50"));

            var txn1 = account.post(TransactionDirection.IN, "USD", first, "First", FIXED_TIME);
            transactionRepository.post(txn1);
            account = accountRepository.findByIdWithBalances(accountId);

            var txn2 = account.post(TransactionDirection.IN, "USD", second, "Second", FIXED_TIME);
            transactionRepository.post(txn2);

            var updated = accountRepository.findByIdWithBalances(accountId);
            assertThat(updated.balance("USD").availableAmount().getAmount()).isEqualByComparingTo(new BigDecimal("1750.50"));

            var persisted = accountRepository.findTransactions(accountId);
            assertThat(persisted).hasSize(3);
        }

        @Test
        void shouldDepositInEurosWhenAccountHasEurosBalance() {
            var depositAmount = MoneyFactory.of("EUR", new BigDecimal("200.00"));
            var transaction = account.post(TransactionDirection.IN, "EUR", depositAmount, "Euro deposit", FIXED_TIME);

            transactionRepository.post(transaction);

            var updated = accountRepository.findByIdWithBalances(accountId);
            assertThat(updated.balance("EUR").availableAmount().getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));

            var transactions = accountRepository.findTransactions(accountId);
            assertThat(transactions).hasSize(1);
            assertThat(transactions.getFirst().currency()).isEqualTo("EUR");
        }
    }

    @Nested
    class PostingTransactionOutDirectionTests {

        @BeforeEach
        void seed() {
            seedBalance();
        }

        @Test
        void shouldWithdrawMoneyAndDecreaseBalance() {
            var withdrawalAmount = MoneyFactory.of("USD", new BigDecimal("300.00"));
            var transaction = account.post(TransactionDirection.OUT, "USD", withdrawalAmount, "ATM withdrawal", FIXED_TIME);

            transactionRepository.post(transaction);

            var updated = accountRepository.findByIdWithBalances(accountId);
            assertThat(updated.balance("USD").availableAmount().getAmount()).isEqualByComparingTo(new BigDecimal("700.00"));

            var transactions = accountRepository.findTransactions(accountId);
            assertThat(transactions).hasSize(2);
            assertThat(transactions.getFirst().direction()).isEqualTo(TransactionDirection.OUT);
            assertThat(transactions.getFirst().description()).isEqualTo("ATM withdrawal");
            assertThat(transactions.getFirst().balanceAfter().getAmount()).isEqualByComparingTo(new BigDecimal("700.00"));
        }

        @Test
        void shouldRejectWithdrawalThatExceedsAvailableBalance() {
            var withdrawalAmount = MoneyFactory.of("USD", new BigDecimal("2000.00"));

            assertThatThrownBy(() -> account.post(TransactionDirection.OUT, "USD", withdrawalAmount, "Overdraft attempt", FIXED_TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");

            var updated = accountRepository.findByIdWithBalances(accountId);
            assertThat(updated.balance("USD").availableAmount().getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        }

        @Test
        void shouldRejectWithdrawalOfExactlyAvailableBalance() {
            var withdrawalAmount = MoneyFactory.of("USD", new BigDecimal("1000.00"));

            assertThatThrownBy(() -> account.post(TransactionDirection.OUT, "USD", withdrawalAmount, "Empty account", FIXED_TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");
        }

        @Test
        void shouldNotAffectOtherCurrencyBalancesOnWithdrawal() {
            var withdrawalAmount = MoneyFactory.of("USD", new BigDecimal("100.00"));
            var transaction = account.post(TransactionDirection.OUT, "USD", withdrawalAmount, "USD withdrawal", FIXED_TIME);

            transactionRepository.post(transaction);

            var updated = accountRepository.findByIdWithBalances(accountId);
            assertThat(updated.balance("USD").availableAmount().getAmount()).isEqualByComparingTo(new BigDecimal("900.00"));
            assertThat(updated.balance("EUR").availableAmount().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class TransactionOrderingAndHistoryTests {

        @Test
        void shouldReturnTransactionsOrderedByCreatedAtDesc() {
            var first = MoneyFactory.of("USD", new BigDecimal("100.00"));
            var t1 = FIXED_TIME;
            var txn1 = account.post(TransactionDirection.IN, "USD", first, "First", t1);
            transactionRepository.post(txn1);

            var second = MoneyFactory.of("USD", new BigDecimal("50.00"));
            var t2 = t1.plusSeconds(1);
            var txn2 = account.post(TransactionDirection.OUT, "USD", second, "Second", t2);
            transactionRepository.post(txn2);

            var third = MoneyFactory.of("USD", new BigDecimal("50.00"));
            var t3 = t2.plusSeconds(1);
            var txn3 = account.post(TransactionDirection.IN, "USD", third, "Third", t3);
            transactionRepository.post(txn3);

            var transactions = accountRepository.findTransactions(accountId);
            assertThat(transactions).hasSize(3);
            assertThat(transactions.get(0).id().value()).isEqualTo(txn3.id().value());
            assertThat(transactions.get(1).id().value()).isEqualTo(txn2.id().value());
            assertThat(transactions.get(2).id().value()).isEqualTo(txn1.id().value());
        }
    }

    private Account createAccount(CustomerId customerId, Set<String> currencies) {
        return new Account(accountId, customerId, COUNTRY, currencies, FIXED_TIME);
    }
}
