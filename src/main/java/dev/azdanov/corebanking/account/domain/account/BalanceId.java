package dev.azdanov.corebanking.account.domain.account;

import dev.azdanov.corebanking.shared.UuidFactory;

import java.util.Objects;
import java.util.UUID;

public record BalanceId(UUID value) {
    public BalanceId {
        Objects.requireNonNull(value);
    }

    public static BalanceId create() {
        return new BalanceId(UuidFactory.generate());
    }
}
