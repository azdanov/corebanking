package dev.azdanov.corebanking.account.domain.account;

import dev.azdanov.corebanking.shared.UuidFactory;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) {
    public TransactionId {
        Objects.requireNonNull(value);
    }

    public static TransactionId create() {
        return new TransactionId(UuidFactory.generate());
    }
}
