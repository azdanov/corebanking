package dev.azdanov.corebanking.account.infrastructure.messaging;

import dev.azdanov.corebanking.account.domain.repository.OutboxRepository;
import dev.azdanov.corebanking.account.domain.repository.PendingOutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitOutboxSchedulerTest {

    private static final String EXCHANGE = "corebanking.account.events";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitOutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RabbitOutboxScheduler(
            outboxRepository,
            rabbitTemplate,
            EXCHANGE,
            BATCH_SIZE,
            MAX_ATTEMPTS
        );
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxRepository.claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS))
            .thenReturn(List.of());

        scheduler.processPendingEvents();

        verify(outboxRepository).claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS);
        verify(rabbitTemplate, never())
            .convertAndSend(anyString(), anyString(), any(Object.class));
        verify(outboxRepository, never()).markProcessed(any(), any());
        verify(outboxRepository, never()).markFailed(any(), any());
    }

    @Test
    void shouldSendAndMarkProcessedForEachPendingEvent() {
        var event1 = new PendingOutboxEvent(
            UUID.randomUUID(),
            "AccountCreatedEvent",
            "account.created",
            "{\"id\":\"a\"}",
            0
        );
        var event2 = new PendingOutboxEvent(
            UUID.randomUUID(),
            "TransactionCreatedEvent",
            "account.transaction.created",
            "{\"id\":\"b\"}",
            2
        );
        when(outboxRepository.claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS))
            .thenReturn(List.of(event1, event2));

        scheduler.processPendingEvents();

        verify(rabbitTemplate).convertAndSend(EXCHANGE, "account.created", "{\"id\":\"a\"}");
        verify(rabbitTemplate).convertAndSend(EXCHANGE, "account.transaction.created", "{\"id\":\"b\"}");
        verify(outboxRepository, times(2)).markProcessed(any(UUID.class), any());
        verify(outboxRepository, never()).markFailed(any(), any());
    }

    @Test
    void shouldMarkFailedWithTrimmedMessageWhenAmqpExceptionIsThrown() {
        var event = new PendingOutboxEvent(
            UUID.randomUUID(),
            "AccountCreatedEvent",
            "account.created",
            "{}",
            0
        );
        when(outboxRepository.claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS))
            .thenReturn(List.of(event));
        var amqpError = new AmqpException("Connection refused");
        doThrowOnConvertAndSend(amqpError);

        scheduler.processPendingEvents();

        verify(outboxRepository).markFailed(event.id(), "Connection refused");
        verify(outboxRepository, never()).markProcessed(any(), any());
    }

    @Test
    void shouldTruncateErrorMessagesLongerThanMaxLength() {
        var event = new PendingOutboxEvent(
            UUID.randomUUID(),
            "AccountCreatedEvent",
            "account.created",
            "{}",
            0
        );
        when(outboxRepository.claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS))
            .thenReturn(List.of(event));
        var longError = "x".repeat(1500);
        doThrowOnConvertAndSend(new AmqpException(longError));

        scheduler.processPendingEvents();

        var errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxRepository).markFailed(eq(event.id()), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).hasSize(1000);
        assertThat(errorCaptor.getValue()).isEqualTo("x".repeat(1000));
    }

    @Test
    void shouldUseUnknownRelayErrorWhenMessageIsBlank() {
        var event = new PendingOutboxEvent(
            UUID.randomUUID(),
            "AccountCreatedEvent",
            "account.created",
            "{}",
            0
        );
        when(outboxRepository.claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS))
            .thenReturn(List.of(event));
        doThrowOnConvertAndSend(new AmqpException(""));

        scheduler.processPendingEvents();

        verify(outboxRepository).markFailed(event.id(), "Unknown relay error");
    }

    @Test
    void shouldUseUnknownRelayErrorWhenMessageIsNull() {
        var event = new PendingOutboxEvent(
            UUID.randomUUID(),
            "AccountCreatedEvent",
            "account.created",
            "{}",
            0
        );
        when(outboxRepository.claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS))
            .thenReturn(List.of(event));
        //noinspection DataFlowIssue
        doThrowOnConvertAndSend(new AmqpException((String) null));

        scheduler.processPendingEvents();

        verify(outboxRepository).markFailed(event.id(), "Unknown relay error");
    }

    @Test
    void shouldContinueProcessingRemainingEventsAfterOneFails() {
        var failed = new PendingOutboxEvent(
            UUID.randomUUID(),
            "AccountCreatedEvent",
            "account.created",
            "{}",
            0
        );
        var ok = new PendingOutboxEvent(
            UUID.randomUUID(),
            "TransactionCreatedEvent",
            "account.transaction.created",
            "{}",
            0
        );
        when(outboxRepository.claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS))
            .thenReturn(List.of(failed, ok));
        doThrow(new AmqpException("boom"))
            .when(rabbitTemplate)
            .convertAndSend(EXCHANGE, "account.created", "{}");

        scheduler.processPendingEvents();

        verify(outboxRepository).markFailed(eq(failed.id()), anyString());
        verify(outboxRepository).markProcessed(eq(ok.id()), any());
        verify(rabbitTemplate).convertAndSend(EXCHANGE, "account.created", "{}");
        verify(rabbitTemplate).convertAndSend(EXCHANGE, "account.transaction.created", "{}");
    }

    @Test
    void shouldClaimBatchWithConfiguredBatchSizeAndMaxAttempts() {
        when(outboxRepository.claimPendingBatch(anyInt(), anyInt()))
            .thenReturn(List.of());

        scheduler.processPendingEvents();

        verify(outboxRepository).claimPendingBatch(BATCH_SIZE, MAX_ATTEMPTS);
    }

    private void doThrowOnConvertAndSend(AmqpException error) {
        doThrow(error).when(rabbitTemplate)
            .convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
