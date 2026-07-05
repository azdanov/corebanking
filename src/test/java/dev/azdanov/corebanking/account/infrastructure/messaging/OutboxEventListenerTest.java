package dev.azdanov.corebanking.account.infrastructure.messaging;

import dev.azdanov.corebanking.account.application.event.AccountCreatedEvent;
import dev.azdanov.corebanking.account.application.event.TransactionCreatedEvent;
import dev.azdanov.corebanking.account.domain.repository.OutboxRepository;
import dev.azdanov.corebanking.shared.exception.InfrastructureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventListenerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-07T10:00:00Z");

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventListener listener;

    @Test
    void shouldSaveAccountCreatedEventWithAccountCreatedRoutingKey() {
        var event = new AccountCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "US",
            List.of("USD", "EUR"),
            FIXED_TIME
        );
        var json = "{\"accountId\":\"abc\"}";
        when(objectMapper.writeValueAsString(event)).thenReturn(json);

        listener.onAccountCreated(event);

        verify(outboxRepository).save("AccountCreatedEvent", "account.created", json, FIXED_TIME);
    }

    @Test
    void shouldSaveTransactionCreatedEventWithTransactionCreatedRoutingKey() {
        var event = new TransactionCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "USD",
            "IN",
            "Salary deposit",
            new BigDecimal("600.00"),
            FIXED_TIME
        );
        var json = "{\"transactionId\":\"abc\"}";
        when(objectMapper.writeValueAsString(event)).thenReturn(json);

        listener.onTransactionCreated(event);

        verify(outboxRepository).save(
            "TransactionCreatedEvent",
            "account.transaction.created",
            json,
            FIXED_TIME
        );
    }

    @Test
    void shouldThrowInfrastructureExceptionWhenJacksonFailsToSerialize() {
        var event = new AccountCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "US",
            List.of("USD"),
            FIXED_TIME
        );
        doThrow(new JacksonException("boom") {})
            .when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> listener.onAccountCreated(event))
            .isInstanceOf(InfrastructureException.class)
            .hasMessage("Failed to serialize outbox payload")
            .hasCauseInstanceOf(JacksonException.class);

        verify(outboxRepository, never()).save(any(), any(), any(), any());
    }
}
