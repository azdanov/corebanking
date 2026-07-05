package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.TestcontainersConfiguration;
import dev.azdanov.corebanking.account.domain.repository.OutboxRepository;
import dev.azdanov.corebanking.account.domain.repository.PendingOutboxEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxRepositoryAdapterTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-07T10:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-05-07T11:00:00Z");
    private static final Instant LATER = Instant.parse("2026-05-07T11:00:05Z");

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID firstId;
    private UUID secondId;

    @BeforeEach
    void setUp() {
        firstId = UUID.randomUUID();
        secondId = UUID.randomUUID();
        // Seed directly via JdbcTemplate (autocommit) so REQUIRES_NEW transactions can see the rows.
        seedRow(firstId, "AccountCreatedEvent", "account.created", EARLIER);
        seedRow(secondId, "TransactionCreatedEvent", "account.transaction.created", LATER);
    }

    private void seedRow(UUID id, String eventType, String routingKey, Instant createdAt) {
        jdbcTemplate.update(
            """
                INSERT INTO outbox_events (id, event_type, routing_key, payload, status, attempts, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), CAST('PENDING' AS outbox_event_status), 0, ?)
                """,
            id, eventType, routingKey, "{}", Timestamp.from(createdAt)
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM outbox_events WHERE id IN (?, ?)", firstId, secondId);
    }

    @Nested
    class SaveTests {

        @Test
        @Transactional
        void shouldInsertEventRowWhenWithinTransaction() {
            var createdAt = Instant.parse("2026-05-07T12:00:00Z");

            outboxRepository.save(
                "AccountCreatedEvent",
                "account.created",
                "{\"accountId\":\"new\"}",
                createdAt
            );

            var row = jdbcTemplate.queryForRowSet(
                """
                    SELECT event_type, routing_key, payload::text AS payload, status::text AS status
                    FROM outbox_events
                    WHERE created_at = ?
                    """,
                Timestamp.from(createdAt)
            );
            assertThat(row.next()).isTrue();
            assertThat(row.getString("event_type")).isEqualTo("AccountCreatedEvent");
            assertThat(row.getString("routing_key")).isEqualTo("account.created");
            assertThat(row.getString("payload")).contains("accountId").contains("new");
            assertThat(row.getString("status")).isEqualTo("PENDING");
        }

        @Test
        void shouldThrowWhenCalledOutsideTransaction() {
            assertThatThrownBy(() -> outboxRepository.save(
                "AccountCreatedEvent",
                "account.created",
                "{}",
                FIXED_TIME
            )).isInstanceOf(IllegalTransactionStateException.class);
        }
    }

    @Nested
    class ClaimPendingBatchTests {

        @Test
        void shouldReturnAllPendingRowsOrderedByCreatedAt() {
            var claimed = outboxRepository.claimPendingBatch(10, 10);

            assertThat(claimed).extracting(PendingOutboxEvent::id).contains(firstId, secondId);
            assertThat(claimed.get(0).id()).isEqualTo(firstId);
            assertThat(claimed.get(1).id()).isEqualTo(secondId);
        }

        @Test
        void shouldRespectLimit() {
            var claimed = outboxRepository.claimPendingBatch(1, 10);

            assertThat(claimed).hasSize(1);
            assertThat(claimed.getFirst().id()).isEqualTo(firstId);
        }

        @Test
        void shouldExcludeRowsAtOrAboveMaxAttempts() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET attempts = ? WHERE id = ?",
                5, firstId
            );

            var claimed = outboxRepository.claimPendingBatch(10, 5);

            assertThat(claimed).extracting(PendingOutboxEvent::id).containsExactly(secondId);
        }

        @Test
        void shouldMarkClaimedRowsAsProcessing() {
            outboxRepository.claimPendingBatch(10, 10);

            var statuses = jdbcTemplate.query(
                "SELECT id, status::text AS status FROM outbox_events WHERE id IN (?, ?)",
                (rs, _) -> rs.getString("status"),
                firstId, secondId
            );

            assertThat(statuses).containsExactly("PROCESSING", "PROCESSING");
        }

        @Test
        void shouldNotClaimRowsAlreadyProcessing() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('PROCESSING' AS outbox_event_status) WHERE id = ?",
                firstId
            );

            var claimed = outboxRepository.claimPendingBatch(10, 10);

            assertThat(claimed).extracting(PendingOutboxEvent::id).containsExactly(secondId);
        }

        @Test
        void shouldOnlyReturnRowsSuccessfullyClaimed() {
            // Pre-mark one row as PROCESSING so markProcessing returns 0 for it.
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('PROCESSING' AS outbox_event_status) WHERE id = ?",
                firstId
            );

            var claimed = outboxRepository.claimPendingBatch(10, 10);

            assertThat(claimed).extracting(PendingOutboxEvent::id).containsExactly(secondId);
            assertThat(claimed.getFirst().eventType()).isEqualTo("TransactionCreatedEvent");
            assertThat(claimed.getFirst().routingKey()).isEqualTo("account.transaction.created");
            assertThat(claimed.getFirst().payload()).isEqualTo("{}");
            assertThat(claimed.getFirst().attempts()).isZero();
        }

        @Test
        void shouldIncludeFailedRows() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('FAILED' AS outbox_event_status), attempts = 1 WHERE id = ?",
                firstId
            );

            var claimed = outboxRepository.claimPendingBatch(10, 10);

            assertThat(claimed).extracting(PendingOutboxEvent::id).contains(firstId, secondId);
            var firstClaim = claimed.stream()
                .filter(e -> e.id().equals(firstId))
                .findFirst()
                .orElseThrow();
            assertThat(firstClaim.attempts()).isEqualTo(1);
        }

        @Test
        void shouldReturnEmptyListWhenNothingPending() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('PROCESSED' AS outbox_event_status) WHERE id IN (?, ?)",
                firstId, secondId
            );

            var claimed = outboxRepository.claimPendingBatch(10, 10);

            assertThat(claimed).isEmpty();
        }
    }

    @Nested
    class MarkProcessedTests {

        @Test
        void shouldSetStatusToProcessedAndProcessedAt() {
            var processedAt = Instant.parse("2026-05-07T11:00:00Z");

            outboxRepository.markProcessed(firstId, processedAt);

            var status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM outbox_events WHERE id = ?",
                String.class, firstId
            );
            assertThat(status).isEqualTo("PROCESSED");
        }
    }

    @Nested
    class MarkFailedTests {

        @Test
        void shouldSetStatusToFailedAndIncrementAttempts() {
            outboxRepository.markFailed(firstId, "Connection refused");

            var status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM outbox_events WHERE id = ?",
                String.class, firstId
            );
            var attempts = jdbcTemplate.queryForObject(
                "SELECT attempts FROM outbox_events WHERE id = ?",
                Integer.class, firstId
            );
            var lastError = jdbcTemplate.queryForObject(
                "SELECT last_error FROM outbox_events WHERE id = ?",
                String.class, firstId
            );

            assertThat(status).isEqualTo("FAILED");
            assertThat(attempts).isEqualTo(1);
            assertThat(lastError).isEqualTo("Connection refused");
        }

        @Test
        void shouldAccumulateAttemptsAcrossMultipleFailures() {
            outboxRepository.markFailed(firstId, "first");
            outboxRepository.markFailed(firstId, "second");

            var attempts = jdbcTemplate.queryForObject(
                "SELECT attempts FROM outbox_events WHERE id = ?",
                Integer.class, firstId
            );

            assertThat(attempts).isEqualTo(2);
        }
    }

}
