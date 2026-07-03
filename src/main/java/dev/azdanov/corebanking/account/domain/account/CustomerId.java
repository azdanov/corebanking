package dev.azdanov.corebanking.account.domain.account;

import dev.azdanov.corebanking.shared.UuidFactory;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(UUID value) {
    public CustomerId {
        Objects.requireNonNull(value);
    }

    public static CustomerId create() {
        return new CustomerId(UuidFactory.generate());
    }
}
