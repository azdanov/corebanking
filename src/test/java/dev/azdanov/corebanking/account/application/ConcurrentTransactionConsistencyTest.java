package dev.azdanov.corebanking.account.application;

import dev.azdanov.corebanking.TestcontainersConfiguration;
import dev.azdanov.corebanking.account.api.model.TransactionDirection;
import dev.azdanov.corebanking.account.api.response.TransactionResponse;
import dev.azdanov.corebanking.account.application.command.CreateAccountCommand;
import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;
import dev.azdanov.corebanking.account.application.commandhandler.CreateTransactionHandler;
import dev.azdanov.corebanking.account.application.commandhandler.OpenAccountHandler;
import dev.azdanov.corebanking.account.application.query.GetAccountQuery;
import dev.azdanov.corebanking.account.application.query.GetTransactionsQuery;
import dev.azdanov.corebanking.account.application.queryhandler.GetAccountHandler;
import dev.azdanov.corebanking.account.application.queryhandler.GetTransactionsHandler;
import dev.azdanov.corebanking.shared.UuidFactory;
import dev.azdanov.corebanking.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that balances and the transaction ledger stay consistent under concurrent load:
 * no lost updates, no missing transactions, no overdrafts, and no stale reads leaking
 * divergent balance values to other callees (API responses, ledger rows, events).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrentTransactionConsistencyTest {

    private static final String CURRENCY = "USD";

    @Autowired
    private OpenAccountHandler openAccountHandler;

    @Autowired
    private CreateTransactionHandler createTransactionHandler;

    @Autowired
    private GetAccountHandler getAccountHandler;

    @Autowired
    private GetTransactionsHandler getTransactionsHandler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdAccountIds = new ArrayList<>();

    /**
     * Unlike @Transactional tests that roll back, this class commits real rows.
     * Remove them so tests assuming a pristine database (e.g. the outbox claim
     * tests) stay deterministic regardless of execution order.
     */
    @AfterEach
    void cleanUpCommittedData() {
        for (var accountId : createdAccountIds) {
            jdbcTemplate.update("DELETE FROM transactions WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM balances WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
        }
        createdAccountIds.clear();
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void shouldKeepBalanceAndLedgerConsistentUnderConcurrentDeposits() throws Exception {
        var accountId = openUsdAccount();
        var threads = 16;
        var depositsPerThread = 5;
        var amount = new BigDecimal("100.00");

        runConcurrently(threads, () -> {
            for (int i = 0; i < depositsPerThread; i++) {
                createTransactionHandler.createTransaction(command(accountId, "IN", amount, "deposit"));
            }
        });

        var totalDeposits = threads * depositsPerThread;
        var expectedBalance = amount.multiply(BigDecimal.valueOf(totalDeposits));

        assertThat(balanceOf(accountId))
            .as("balance must equal the sum of all deposits (no lost updates)")
            .isEqualByComparingTo(expectedBalance);

        var ledger = ledgerOf(accountId);
        assertThat(ledger)
            .as("every accepted deposit must appear in the ledger (no missing transactions)")
            .hasSize(totalDeposits);
        assertThat(ledger.stream().map(TransactionResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
            .as("ledger amounts must add up to the balance")
            .isEqualByComparingTo(expectedBalance);

        var expectedChain = IntStream.rangeClosed(1, totalDeposits)
            .mapToObj(i -> amount.multiply(BigDecimal.valueOf(i)))
            .toList();
        assertThat(ledger.stream().map(TransactionResponse::balanceAfter).toList())
            .as("recorded balance_after values must be the exact serialized chain (no stale reads)")
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactlyInAnyOrderElementsOf(expectedChain);
    }

    @Test
    void shouldNotOverdrawBalanceUnderConcurrentWithdrawals() throws Exception {
        var accountId = openUsdAccount();
        createTransactionHandler.createTransaction(
            command(accountId, "IN", new BigDecimal("1000.00"), "seed"));

        var threads = 25;
        var amount = new BigDecimal("100.00");
        var accepted = new AtomicInteger();
        var rejected = new ConcurrentLinkedQueue<BusinessRuleException>();

        runConcurrently(threads, () -> {
            try {
                createTransactionHandler.createTransaction(command(accountId, "OUT", amount, "withdraw"));
                accepted.incrementAndGet();
            } catch (BusinessRuleException e) {
                rejected.add(e);
            }
        });

        assertThat(accepted.get())
            .as("only withdrawals covered by the available balance may succeed")
            .isEqualTo(10);
        assertThat(rejected).hasSize(threads - 10);

        assertThat(balanceOf(accountId))
            .as("balance must never go negative or diverge from accepted withdrawals")
            .isEqualByComparingTo(new BigDecimal("0.00"));

        var withdrawals = ledgerOf(accountId).stream()
            .filter(transaction -> transaction.direction() == TransactionDirection.OUT)
            .toList();
        assertThat(withdrawals)
            .as("each accepted withdrawal must appear in the ledger exactly once")
            .hasSize(accepted.get());
    }

    @Test
    void shouldOnlyExposeCommittedBalancesToConcurrentReaders() throws Exception {
        var accountId = openUsdAccount();
        var writerThreads = 8;
        var depositsPerWriter = 10;
        var amount = new BigDecimal("50.00");
        var maxBalance = amount.multiply(BigDecimal.valueOf(writerThreads * depositsPerWriter));

        var stop = new AtomicBoolean(false);
        var divergentReads = new ConcurrentLinkedQueue<BigDecimal>();
        var readers = Executors.newFixedThreadPool(4);

        try {
            for (int i = 0; i < 4; i++) {
                readers.execute(() -> {
                    while (!stop.get()) {
                        var observed = balanceOf(accountId);
                        // With equal-sized deposits from zero, any committed balance is k * amount.
                        var committedPartialSum = observed.signum() >= 0
                            && observed.compareTo(maxBalance) <= 0
                            && observed.remainder(amount).signum() == 0;
                        if (!committedPartialSum) {
                            divergentReads.add(observed);
                        }
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                });
            }

            runConcurrently(writerThreads, () -> {
                for (int i = 0; i < depositsPerWriter; i++) {
                    createTransactionHandler.createTransaction(command(accountId, "IN", amount, "deposit"));
                }
            });
        } finally {
            stop.set(true);
            readers.shutdown();
            assertThat(readers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(divergentReads)
            .as("readers must never observe a balance that is not a committed partial sum")
            .isEmpty();
        assertThat(balanceOf(accountId))
            .as("final balance must equal the sum of all deposits")
            .isEqualByComparingTo(maxBalance);
    }

    @Test
    void shouldKeepLedgerCompleteAndBalancedUnderMixedConcurrentLoad() throws Exception {
        var accountId = openUsdAccount();
        var seed = new BigDecimal("10000.00");
        createTransactionHandler.createTransaction(command(accountId, "IN", seed, "seed"));

        var threads = 16;
        var operationsPerThread = 6;
        var amount = new BigDecimal("10.00");

        runConcurrently(threads, () -> {
            for (int i = 0; i < operationsPerThread; i++) {
                var direction = ThreadLocalRandom.current().nextBoolean() ? "IN" : "OUT";
                createTransactionHandler.createTransaction(command(accountId, direction, amount, "load"));
            }
        });

        var operations = threads * operationsPerThread;
        var ledger = ledgerOf(accountId).stream()
            .filter(transaction -> "load".equals(transaction.description()))
            .toList();
        assertThat(ledger)
            .as("every accepted operation must appear in the ledger (no missing transactions)")
            .hasSize(operations);

        var netAmount = ledger.stream()
            .map(transaction -> transaction.direction() == TransactionDirection.IN
                ? transaction.amount()
                : transaction.amount().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var balance = balanceOf(accountId);
        assertThat(balance)
            .as("balance must equal the seed plus the net ledger amount (no divergence)")
            .isEqualByComparingTo(seed.add(netAmount));
        assertThat(balance.signum())
            .as("balance must never go negative")
            .isNotNegative();
    }

    private UUID openUsdAccount() {
        var accountId = openAccountHandler
            .createAccount(new CreateAccountCommand(UuidFactory.generate(), "US", List.of(CURRENCY)))
            .accountId();
        createdAccountIds.add(accountId);
        return accountId;
    }

    private CreateTransactionCommand command(UUID accountId, String direction, BigDecimal amount, String description) {
        return new CreateTransactionCommand(accountId, amount, CURRENCY, direction, description);
    }

    private BigDecimal balanceOf(UUID accountId) {
        return getAccountHandler.handle(new GetAccountQuery(accountId)).balances().stream()
            .filter(balance -> CURRENCY.equals(balance.currency()))
            .findFirst()
            .orElseThrow()
            .availableAmount();
    }

    private List<TransactionResponse> ledgerOf(UUID accountId) {
        return getTransactionsHandler.handle(new GetTransactionsQuery(accountId));
    }

    private void runConcurrently(int threads, ThrowingTask task) throws Exception {
        var executor = Executors.newFixedThreadPool(threads);
        try {
            var barrier = new CyclicBarrier(threads);
            var futures = IntStream.range(0, threads)
                .mapToObj(i -> executor.submit(() -> {
                    barrier.await(30, TimeUnit.SECONDS);
                    task.run();
                    return null;
                }))
                .toList();
            for (var future : futures) {
                try {
                    future.get(120, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof Exception exception) {
                        throw exception;
                    }
                    throw new AssertionError("Concurrent task failed", e);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingTask {
        void run() throws Exception;
    }
}
