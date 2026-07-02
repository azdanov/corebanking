package dev.azdanov.corebanking.account.domain.account;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {
    public AccountId {
        Objects.requireNonNull(value);
    }

    public static AccountId generate() {
        return new AccountId(UUID.ofEpochMillis(System.currentTimeMillis()));
    }
}
