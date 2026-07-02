package dev.azdanov.corebanking.account.api;

import dev.azdanov.corebanking.account.api.request.CreateAccountRequest;
import dev.azdanov.corebanking.account.api.request.CreateTransactionRequest;
import dev.azdanov.corebanking.account.api.response.AccountResponse;
import dev.azdanov.corebanking.account.api.response.TransactionResponse;
import dev.azdanov.corebanking.account.application.command.CreateAccountCommand;
import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;
import dev.azdanov.corebanking.account.application.commandhandler.CreateTransactionHandler;
import dev.azdanov.corebanking.account.application.commandhandler.OpenAccountHandler;
import dev.azdanov.corebanking.account.application.query.GetAccountQuery;
import dev.azdanov.corebanking.account.application.query.GetTransactionsQuery;
import dev.azdanov.corebanking.account.application.queryhandler.GetAccountHandler;
import dev.azdanov.corebanking.account.application.queryhandler.GetTransactionsHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final OpenAccountHandler openAccountHandler;
    private final CreateTransactionHandler createTransactionHandler;
    private final GetAccountHandler getAccountHandler;
    private final GetTransactionsHandler getTransactionsHandler;

    public AccountController(
        OpenAccountHandler openAccountHandler,
        CreateTransactionHandler createTransactionHandler,
        GetAccountHandler getAccountHandler,
        GetTransactionsHandler getTransactionsHandler
    ) {
        this.openAccountHandler = openAccountHandler;
        this.createTransactionHandler = createTransactionHandler;
        this.getAccountHandler = getAccountHandler;
        this.getTransactionsHandler = getTransactionsHandler;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
        @Valid @RequestBody CreateAccountRequest request
    ) {
        var createAccount = new CreateAccountCommand(request.customerId(), request.country(), request.currencies());
        var account = openAccountHandler.createAccount(createAccount);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
        @PathVariable UUID accountId
    ) {
        var account = getAccountHandler.handle(new GetAccountQuery(accountId));
        return ResponseEntity.ok(account);
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(
        @PathVariable UUID accountId,
        @Valid @RequestBody CreateTransactionRequest request
    ) {
        var createTransaction = new CreateTransactionCommand(request.amount(), request.currency(), request.direction(), request.description());
        var transaction = createTransactionHandler.createTransaction(accountId, createTransaction);
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<List<TransactionResponse>> listTransactions(
        @PathVariable UUID accountId
    ) {
        var transactions = getTransactionsHandler.handle(new GetTransactionsQuery(accountId));
        return ResponseEntity.ok(transactions);
    }
}
