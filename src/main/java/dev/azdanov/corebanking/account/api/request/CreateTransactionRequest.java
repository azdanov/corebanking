package dev.azdanov.corebanking.account.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateTransactionRequest(
    @NotNull BigDecimal amount,
    @NotBlank String currency,
    @NotBlank String direction,
    @NotBlank String description
) {
}
