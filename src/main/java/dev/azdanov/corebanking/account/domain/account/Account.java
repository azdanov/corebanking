package dev.azdanov.corebanking.account.domain.account;

import dev.azdanov.corebanking.shared.money.MoneyFactory;
import org.joda.money.Money;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Account {
    private final AccountId id;
    private final CustomerId customerId;
    private final String country;
    private final Map<String, Balance> balances;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Account(
        AccountId id,
        CustomerId customerId,
        String country,
        Set<String> currencies,
        Instant now
    ) {
        this.id = id;
        this.customerId = customerId;
        this.country = country;
        this.balances = new LinkedHashMap<>();
        this.createdAt = now;
        this.updatedAt = now;
        currencies.forEach(
            currency -> balances.put(currency,
                new Balance(id, currency, MoneyFactory.zero(currency))
            )
        );
    }

    public AccountId id() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public String country() {
        return country;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Map<String, Balance> balances() {
        return balances;
    }

    public Balance balance(String currency) {
        Balance balance = balances.get(currency);
        if (balance == null) {
            throw new IllegalArgumentException("Balance not found for currency " + currency);
        }
        return balance;
    }

    public Transaction post(
        TransactionDirection direction,
        String currency,
        Money amount,
        String description,
        Instant now
    ) {
        Balance current = balance(currency);
        Money nextAmount =
            switch (direction) {
                case IN -> current.availableAmount().plus(amount);
                case OUT -> {
                    Money next = current.availableAmount().minus(amount);
                    if (next.isNegativeOrZero()) {
                        throw new IllegalStateException("Insufficient funds");
                    }
                    yield next;
                }
            };
        current.setAvailableAmount(nextAmount);
        return new Transaction(
            TransactionId.generate(),
            id,
            amount,
            currency,
            direction,
            description,
            nextAmount,
            now,
            now
        );
    }
}
