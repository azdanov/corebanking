package dev.azdanov.corebanking.account.domain.account;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value);
    }

    public static TransactionId generate() {
        return new TransactionId(UUID.ofEpochMillis(System.currentTimeMillis()));
    }
}
