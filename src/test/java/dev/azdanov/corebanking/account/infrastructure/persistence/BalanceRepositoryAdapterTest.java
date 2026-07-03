package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.TestcontainersConfiguration;
import dev.azdanov.corebanking.account.domain.account.Account;
import dev.azdanov.corebanking.account.domain.account.AccountId;
import dev.azdanov.corebanking.account.domain.account.CustomerId;
import dev.azdanov.corebanking.account.domain.repository.AccountRepository;
import dev.azdanov.corebanking.account.domain.repository.BalanceRepository;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.AccountMapper;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.BalanceMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("BalanceRepositoryAdapter - persistence tests")
class BalanceRepositoryAdapterTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-07T10:00:00Z");
    private static final String COUNTRY = "US";

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceMapper balanceMapper;

    @Autowired
    private AccountMapper accountMapper;

    private AccountId accountId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        accountId = AccountId.create();
        customerId = CustomerId.create();
    }

    @Nested
    @DisplayName("createInitialBalances")
    class CreateInitialBalancesTests {

        @Test
        @DisplayName("should insert balance rows for each requested currency")
        void shouldInsertBalancesForEachCurrency() {
            var account = createAccount(Set.of());
            accountRepository.save(account);

            balanceRepository.createInitialBalances(accountId, List.of("USD", "EUR", "GBP"));

            var balances = accountMapper.findBalancesByAccountId(accountId.value());
            assertThat(balances).hasSize(3);

            var usdBalance = balances.stream().filter(b -> "USD".equals(b.currency())).findFirst().orElseThrow();
            var eurBalance = balances.stream().filter(b -> "EUR".equals(b.currency())).findFirst().orElseThrow();
            var gbpBalance = balances.stream().filter(b -> "GBP".equals(b.currency())).findFirst().orElseThrow();

            assertThat(usdBalance.accountId()).isEqualTo(accountId.value());
            assertThat(usdBalance.availableAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(eurBalance.accountId()).isEqualTo(accountId.value());
            assertThat(eurBalance.availableAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(gbpBalance.accountId()).isEqualTo(accountId.value());
            assertThat(gbpBalance.availableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName(
            "should not create duplicate balances when currencies already exist"
        )
        void shouldNotCreateDuplicateBalances() {
            var account = createAccount(Set.of());
            accountRepository.save(account);
            balanceRepository.createInitialBalances(accountId, List.of("USD", "EUR"));

            balanceRepository.createInitialBalances(accountId, List.of("USD", "GBP"));

            var balances = accountMapper.findBalancesByAccountId(accountId.value());
            assertThat(balances).hasSize(3);

            var currencies = new ArrayList<String>();
            for (var balance : balances) {
                currencies.add(balance.currency());
            }
            assertThat(currencies).containsExactlyInAnyOrder("USD", "EUR", "GBP");
        }

        @Test
        @DisplayName("should create a single balance row for one currency")
        void shouldCreateSingleBalance() {
            var account = createAccount(Set.of());
            accountRepository.save(account);

            balanceRepository.createInitialBalances(accountId, List.of("SEK"));

            var balances = accountMapper.findBalancesByAccountId(accountId.value());
            assertThat(balances).hasSize(1);
            assertThat(balances.getFirst().currency()).isEqualTo("SEK");
            assertThat(balances.getFirst().availableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should not insert any rows when no currencies provided")
        void shouldInsertNothingWhenNoCurrencies() {
            var account = createAccount(Set.of());
            accountRepository.save(account);

            var balances = accountMapper.findBalancesByAccountId(accountId.value());
            assertThat(balances).isEmpty();
        }
    }

    @Nested
    @DisplayName("BalanceMapper - increment and decrement operations")
    class BalanceIncrementDecrementTests {

        @Test
        @DisplayName("should increment balance for a deposit")
        void shouldIncrementBalance() {
            var account = createAccount(Set.of());
            accountRepository.save(account);
            balanceRepository.createInitialBalances(accountId, List.of("USD"));

            var depositAmount = new BigDecimal("500.00");

            var updatedRows = balanceMapper.incrementBalance(accountId.value(), "USD", depositAmount);

            assertThat(updatedRows).isEqualTo(1);
            var balance = balanceMapper.findAvailableAmount(accountId.value(), "USD");
            assertThat(balance).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("should accumulate multiple deposits on the same balance")
        void shouldAccumulateDeposits() {
            var account = createAccount(Set.of());
            accountRepository.save(account);
            balanceRepository.createInitialBalances(accountId, List.of("EUR"));

            balanceMapper.incrementBalance(
                accountId.value(),
                "EUR",
                new BigDecimal("100.00")
            );
            balanceMapper.incrementBalance(
                accountId.value(),
                "EUR",
                new BigDecimal("250.50")
            );

            var balance = balanceMapper.findAvailableAmount(accountId.value(), "EUR");
            assertThat(balance).isEqualByComparingTo(new BigDecimal("350.50"));
        }

        @Test
        @DisplayName("should decrement balance for a withdrawal")
        void shouldDecrementBalance() {
            var account = createAccount(Set.of());
            accountRepository.save(account);
            balanceRepository.createInitialBalances(accountId, List.of("GBP"));
            balanceMapper.incrementBalance(accountId.value(), "GBP", new BigDecimal("200.00"));

            var updatedRows = balanceMapper.decrementBalanceIfEnough(accountId.value(), "GBP", new BigDecimal("75.00"));

            assertThat(updatedRows).isEqualTo(1);
            var balance = balanceMapper.findAvailableAmount(accountId.value(), "GBP");
            assertThat(balance).isEqualByComparingTo(new BigDecimal("125.00"));
        }

        @Test
        @DisplayName("should not decrement balance when insufficient funds")
        void shouldNotDecrementWhenInsufficientFunds() {
            var account = createAccount(Set.of());
            accountRepository.save(account);
            balanceRepository.createInitialBalances(accountId, List.of("USD"));
            balanceMapper.incrementBalance(accountId.value(), "USD", new BigDecimal("50.00"));

            var updatedRows = balanceMapper.decrementBalanceIfEnough(accountId.value(), "USD", new BigDecimal("100.00"));

            assertThat(updatedRows).isZero();
            var balance = balanceMapper.findAvailableAmount(accountId.value(), "USD");
            assertThat(balance).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName(
            "should allow exact withdrawal that brings balance to zero"
        )
        void shouldAllowExactWithdrawal() {
            var account = createAccount(Set.of());
            accountRepository.save(account);
            balanceRepository.createInitialBalances(accountId, List.of("USD"));
            balanceMapper.incrementBalance(accountId.value(), "USD", new BigDecimal("100.00"));

            var updatedRows = balanceMapper.decrementBalanceIfEnough(accountId.value(), "USD", new BigDecimal("100.00"));

            assertThat(updatedRows).isEqualTo(1);
            var balance = balanceMapper.findAvailableAmount(accountId.value(), "USD");
            assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName(
            "should not affect other currency balances when updating one"
        )
        void shouldNotAffectOtherCurrencies() {
            var account = createAccount(Set.of());
            accountRepository.save(account);
            balanceRepository.createInitialBalances(accountId, List.of("USD", "EUR"));
            balanceMapper.incrementBalance(accountId.value(), "USD", new BigDecimal("100.00"));
            balanceMapper.incrementBalance(accountId.value(), "EUR", new BigDecimal("200.00"));

            balanceMapper.decrementBalanceIfEnough(accountId.value(), "USD", new BigDecimal("30.00"));

            assertThat(balanceMapper.findAvailableAmount(accountId.value(), "USD")).isEqualByComparingTo(new BigDecimal("70.00"));
            assertThat(balanceMapper.findAvailableAmount(accountId.value(), "EUR")).isEqualByComparingTo(new BigDecimal("200.00"));
        }
    }

    private Account createAccount(Set<String> currencies) {
        return new Account(accountId, customerId, COUNTRY, currencies, FIXED_TIME);
    }
}
