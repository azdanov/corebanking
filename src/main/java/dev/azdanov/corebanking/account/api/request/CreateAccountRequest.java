package dev.azdanov.corebanking.account.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateAccountRequest(
    @NotNull UUID customerId,
    @NotBlank String country,
    @NotEmpty List<String> currencies
) {
}
