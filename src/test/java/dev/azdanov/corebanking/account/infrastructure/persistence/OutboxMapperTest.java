package dev.azdanov.corebanking.account.infrastructure.persistence;

import dev.azdanov.corebanking.TestcontainersConfiguration;
import dev.azdanov.corebanking.account.infrastructure.persistence.mapper.OutboxMapper;
import dev.azdanov.corebanking.account.infrastructure.persistence.model.OutboxEventRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OutboxMapperTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-07T10:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-05-07T11:00:00Z");
    private static final Instant LATER = Instant.parse("2026-05-07T11:00:05Z");

    @Autowired
    private OutboxMapper outboxMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID firstId;
    private UUID secondId;

    @BeforeEach
    void setUp() {
        firstId = UUID.randomUUID();
        secondId = UUID.randomUUID();
        outboxMapper.insert(firstId, "AccountCreatedEvent", "account.created", "{}", EARLIER);
        outboxMapper.insert(secondId, "TransactionCreatedEvent", "account.transaction.created", "{}", LATER);
    }

    @Nested
    class FindPendingBatchTests {

        @Test
        void shouldReturnPendingRowsOrderedByCreatedAt() {
            var rows = outboxMapper.findPendingBatch(10, 10);

            assertThat(rows).extracting(OutboxEventRow::id).contains(firstId, secondId);
            assertThat(rows.get(0).id()).isEqualTo(firstId);
            assertThat(rows.get(1).id()).isEqualTo(secondId);
        }

        @Test
        void shouldRespectLimit() {
            var rows = outboxMapper.findPendingBatch(1, 10);

            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().id()).isEqualTo(firstId);
        }

        @Test
        void shouldExcludeRowsWithAttemptsAtOrAboveMaxAttempts() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET attempts = ? WHERE id = ?",
                5, firstId
            );

            var rows = outboxMapper.findPendingBatch(10, 5);

            assertThat(rows).extracting(OutboxEventRow::id).containsExactly(secondId);
        }

        @Test
        void shouldIncludeFailedRows() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('FAILED' AS outbox_event_status) WHERE id = ?",
                firstId
            );

            var rows = outboxMapper.findPendingBatch(10, 10);

            assertThat(rows).extracting(OutboxEventRow::id).contains(firstId, secondId);
        }

        @Test
        void shouldExcludeProcessedRows() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('PROCESSED' AS outbox_event_status) WHERE id = ?",
                firstId
            );

            var rows = outboxMapper.findPendingBatch(10, 10);

            assertThat(rows).extracting(OutboxEventRow::id).containsExactly(secondId);
        }

        @Test
        void shouldExcludeProcessingRows() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('PROCESSING' AS outbox_event_status) WHERE id = ?",
                firstId
            );

            var rows = outboxMapper.findPendingBatch(10, 10);

            assertThat(rows).extracting(OutboxEventRow::id).containsExactly(secondId);
        }

        @Test
        void shouldReturnPayloadAsText() {
            var id = UUID.randomUUID();
            outboxMapper.insert(
                id,
                "AccountCreatedEvent",
                "account.created",
                "{\"accountId\":\"abc-123\"}",
                LATER
            );

            var rows = outboxMapper.findPendingBatch(10, 10);

            // PostgreSQL jsonb normalizes whitespace, so the payload comes back with a space after the colon.
            var row = rows.stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow();
            assertThat(row.payload()).contains("accountId").contains("abc-123");
        }

        @Test
        void shouldReturnAttemptsValue() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET attempts = ? WHERE id = ?",
                3, firstId
            );

            var rows = outboxMapper.findPendingBatch(10, 10);

            var first = rows.stream().filter(r -> r.id().equals(firstId)).findFirst().orElseThrow();
            assertThat(first.attempts()).isEqualTo(3);
        }
    }

    @Nested
    class MarkProcessingTests {

        @Test
        void shouldReturnOneWhenRowIsPending() {
            int updated = outboxMapper.markProcessing(firstId);

            assertThat(updated).isEqualTo(1);
        }

        @Test
        void shouldReturnZeroWhenRowIsAlreadyProcessing() {
            outboxMapper.markProcessing(firstId);

            int updated = outboxMapper.markProcessing(firstId);

            assertThat(updated).isZero();
        }

        @Test
        void shouldReturnZeroWhenRowIsProcessed() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('PROCESSED' AS outbox_event_status) WHERE id = ?",
                firstId
            );

            int updated = outboxMapper.markProcessing(firstId);

            assertThat(updated).isZero();
        }

        @Test
        void shouldReturnOneWhenRowIsFailed() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET status = CAST('FAILED' AS outbox_event_status) WHERE id = ?",
                firstId
            );

            int updated = outboxMapper.markProcessing(firstId);

            assertThat(updated).isEqualTo(1);
        }
    }

    @Nested
    class MarkProcessedTests {

        @Test
        void shouldSetStatusToProcessedAndProcessedAt() {
            var processedAt = Instant.parse("2026-05-07T11:00:00Z");

            outboxMapper.markProcessed(firstId, processedAt);

            var status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM outbox_events WHERE id = ?",
                String.class, firstId
            );
            var storedProcessedAt = jdbcTemplate.queryForObject(
                "SELECT processed_at FROM outbox_events WHERE id = ?",
                Instant.class, firstId
            );

            assertThat(status).isEqualTo("PROCESSED");
            assertThat(storedProcessedAt).isEqualTo(processedAt);
        }

        @Test
        void shouldClearLastErrorWhenMarkedProcessed() {
            jdbcTemplate.update(
                "UPDATE outbox_events SET last_error = 'previous failure' WHERE id = ?",
                firstId
            );

            outboxMapper.markProcessed(firstId, FIXED_TIME);

            var lastError = jdbcTemplate.queryForObject(
                "SELECT last_error FROM outbox_events WHERE id = ?",
                String.class, firstId
            );

            assertThat(lastError).isNull();
        }
    }

    @Nested
    class MarkFailedTests {

        @Test
        void shouldSetStatusToFailedAndIncrementAttempts() {
            outboxMapper.markFailed(firstId, "Connection refused");

            var status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM outbox_events WHERE id = ?",
                String.class, firstId
            );
            var attempts = jdbcTemplate.queryForObject(
                "SELECT attempts FROM outbox_events WHERE id = ?",
                Integer.class, firstId
            );

            assertThat(status).isEqualTo("FAILED");
            assertThat(attempts).isEqualTo(1);
        }

        @Test
        void shouldStoreLastError() {
            outboxMapper.markFailed(firstId, "Connection refused");

            var lastError = jdbcTemplate.queryForObject(
                "SELECT last_error FROM outbox_events WHERE id = ?",
                String.class, firstId
            );

            assertThat(lastError).isEqualTo("Connection refused");
        }

        @Test
        void shouldAccumulateAttemptsAcrossMultipleFailures() {
            outboxMapper.markFailed(firstId, "first failure");
            outboxMapper.markFailed(firstId, "second failure");

            var attempts = jdbcTemplate.queryForObject(
                "SELECT attempts FROM outbox_events WHERE id = ?",
                Integer.class, firstId
            );

            assertThat(attempts).isEqualTo(2);
        }
    }
}
