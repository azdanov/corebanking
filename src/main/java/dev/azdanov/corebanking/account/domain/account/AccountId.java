package dev.azdanov.corebanking.account.domain.account;

import dev.azdanov.corebanking.shared.UuidFactory;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {
    public AccountId {
        Objects.requireNonNull(value);
    }

    public static AccountId create() {
        return new AccountId(UuidFactory.generate());
    }
}
