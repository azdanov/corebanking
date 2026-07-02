package dev.azdanov.corebanking.account.domain.account;

import org.joda.money.Money;

import java.time.Instant;

public record Transaction(
    TransactionId id,
    AccountId accountId,
    Money amount,
    String currency,
    TransactionDirection direction,
    String description,
    Money balanceAfter,
    Instant valueTime,
    Instant bookingTime) {
}
