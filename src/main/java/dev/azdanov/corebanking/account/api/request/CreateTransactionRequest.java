package dev.azdanov.corebanking.account.api.request;

import dev.azdanov.corebanking.account.api.model.TransactionDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateTransactionRequest(
    @NotNull BigDecimal amount,
    @NotBlank String currency,
    @NotNull TransactionDirection direction,
    @NotBlank String description
) {
}
