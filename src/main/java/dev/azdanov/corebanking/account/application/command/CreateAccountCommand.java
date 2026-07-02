package dev.azdanov.corebanking.account.application.command;

import java.util.List;
import java.util.UUID;

public record CreateAccountCommand(
    UUID customerId,
    String country,
    List<String> currencies
) {
}
