package dev.azdanov.corebanking.account.domain.account;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(UUID value) {
    public CustomerId {
        Objects.requireNonNull(value);
    }
}
