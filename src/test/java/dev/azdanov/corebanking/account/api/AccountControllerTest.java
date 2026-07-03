package dev.azdanov.corebanking.account.api;

import dev.azdanov.corebanking.account.api.model.TransactionDirection;
import dev.azdanov.corebanking.account.api.request.CreateAccountRequest;
import dev.azdanov.corebanking.account.api.request.CreateTransactionRequest;
import dev.azdanov.corebanking.account.api.response.AccountResponse;
import dev.azdanov.corebanking.account.api.response.BalanceResponse;
import dev.azdanov.corebanking.account.api.response.TransactionResponse;
import dev.azdanov.corebanking.account.application.command.CreateAccountCommand;
import dev.azdanov.corebanking.account.application.command.CreateTransactionCommand;
import dev.azdanov.corebanking.account.application.commandhandler.CreateTransactionHandler;
import dev.azdanov.corebanking.account.application.commandhandler.OpenAccountHandler;
import dev.azdanov.corebanking.account.application.query.GetAccountQuery;
import dev.azdanov.corebanking.account.application.query.GetTransactionsQuery;
import dev.azdanov.corebanking.account.application.queryhandler.GetAccountHandler;
import dev.azdanov.corebanking.account.application.queryhandler.GetTransactionsHandler;
import dev.azdanov.corebanking.shared.UuidFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@DisplayName("AccountController")
class AccountControllerTest {

    private static final String ACCOUNTS_PATH = "/api/v1/accounts";
    private static final String ACCOUNT_PATH = ACCOUNTS_PATH + "/{accountId}";
    private static final String TRANSACTIONS_PATH = ACCOUNT_PATH + "/transactions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OpenAccountHandler openAccountHandler;

    @MockitoBean
    private CreateTransactionHandler createTransactionHandler;

    @MockitoBean
    private GetAccountHandler getAccountHandler;

    @MockitoBean
    private GetTransactionsHandler getTransactionsHandler;

    @Nested
    @DisplayName("POST " + ACCOUNTS_PATH)
    class CreateAccountTests {

        @Test
        @DisplayName("should create account with valid request and return 201 Created")
        void shouldCreateAccount() throws Exception {
            var accountId = UuidFactory.generate();
            var customerId = UuidFactory.generate();
            var balances = List.of(new BalanceResponse[]{new BalanceResponse("USD", BigDecimal.ZERO),});
            var expected = new AccountResponse(accountId, customerId, balances);

            given(openAccountHandler.createAccount(any(CreateAccountCommand.class))).willReturn(expected);

            var request = new CreateAccountRequest(customerId, "US", List.of("USD"));

            postAccount(request)
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.balances", hasSize(1)))
                .andExpect(jsonPath("$.balances[0].currency").value("USD"))
                .andExpect(jsonPath("$.balances[0].availableAmount").value(0));

            verify(openAccountHandler).createAccount(assertArg(cmd -> {
                assertThat(cmd.customerId()).isEqualTo(customerId);
                assertThat(cmd.country()).isEqualTo("US");
                assertThat(cmd.currencies()).containsExactly("USD");
            }));
        }

        @ParameterizedTest
        @MethodSource("invalidRequests")
        @DisplayName("should return 400 for invalid request")
        void shouldReturn400ForInvalidRequest(CreateAccountRequest request) throws Exception {
            postAccount(request).andExpect(status().isBadRequest());

            verify(openAccountHandler, never()).createAccount(any());
        }

        @Test
        @DisplayName("should return 400 when request body is missing")
        void shouldReturn400WhenRequestBodyMissing() throws Exception {
            mockMvc.perform(post(ACCOUNTS_PATH).contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());

            verify(openAccountHandler, never()).createAccount(any());
        }

        static Stream<Arguments> invalidRequests() {
            return Stream.of(
                Arguments.of(new CreateAccountRequest(null, "US", List.of("USD"))),
                Arguments.of(new CreateAccountRequest(UuidFactory.generate(), "", List.of("USD"))),
                Arguments.of(new CreateAccountRequest(UuidFactory.generate(), "US", List.of())),
                Arguments.of(new CreateAccountRequest(UuidFactory.generate(), null, List.of("USD"))),
                Arguments.of(new CreateAccountRequest(UuidFactory.generate(), "US", null))
            );
        }
    }

    @Nested
    @DisplayName("GET " + ACCOUNT_PATH)
    class GetAccountTests {

        @Test
        @DisplayName("should return account with valid accountId")
        void shouldReturnAccount() throws Exception {
            var accountId = UuidFactory.generate();
            var customerId = UuidFactory.generate();
            var balances = List.of(new BalanceResponse[]{new BalanceResponse("GBP", new BigDecimal("1000.00")),});
            var expected = new AccountResponse(accountId, customerId, balances);

            given(getAccountHandler.handle(any(GetAccountQuery.class))).willReturn(expected);

            getAccount(accountId)
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.balances[0].currency").value("GBP"))
                .andExpect(jsonPath("$.balances[0].availableAmount").value("1000.00"));

            verify(getAccountHandler).handle(assertArg(q -> assertThat(q.accountId()).isEqualTo(accountId)));
        }

        @Test
        @DisplayName("should return account with multiple balances")
        void shouldReturnAccountWithMultipleBalances() throws Exception {
            var accountId = UuidFactory.generate();
            var customerId = UuidFactory.generate();
            var balances = List.of(new BalanceResponse[]{
                new BalanceResponse("USD", new BigDecimal("1000.00")),
                new BalanceResponse("EUR", new BigDecimal("500.00")),
            });
            var expected = new AccountResponse(accountId, customerId, balances);

            given(getAccountHandler.handle(any(GetAccountQuery.class))).willReturn(expected);

            getAccount(accountId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.balances", hasSize(2)))
                .andExpect(jsonPath("$.balances[0].currency").value("USD"))
                .andExpect(jsonPath("$.balances[0].availableAmount").value("1000.00"))
                .andExpect(jsonPath("$.balances[1].currency").value("EUR"))
                .andExpect(jsonPath("$.balances[1].availableAmount").value("500.00"));
        }
    }

    @Nested
    @DisplayName("POST " + TRANSACTIONS_PATH)
    class CreateTransactionTests {

        @Test
        @DisplayName("should create transaction with valid request and return 201 Created")
        void shouldCreateTransaction() throws Exception {
            var accountId = UuidFactory.generate();
            var transactionId = UuidFactory.generate();
            var amount = new BigDecimal("250.00");
            var expectedResponse = new TransactionResponse(
                accountId,
                transactionId,
                amount,
                "EUR",
                TransactionDirection.OUT,
                "Test payment",
                new BigDecimal("9750.00")
            );

            given(createTransactionHandler.createTransaction(any(CreateTransactionCommand.class))).willReturn(expectedResponse);

            var request = new CreateTransactionRequest(
                accountId, amount, "EUR", TransactionDirection.OUT, "Test payment");

            postTransaction(accountId, request)
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.amount").value(amount))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.direction").value("OUT"))
                .andExpect(jsonPath("$.description").value("Test payment"))
                .andExpect(jsonPath("$.balanceAfter").value("9750.00"));

            verify(createTransactionHandler).createTransaction(
                assertArg(cmd -> {
                    assertThat(cmd.accountId()).isEqualTo(accountId);
                    assertThat(cmd.amount()).isEqualByComparingTo(amount);
                    assertThat(cmd.currency()).isEqualTo("EUR");
                    assertThat(cmd.direction()).isEqualTo(TransactionDirection.OUT.name());
                    assertThat(cmd.description()).isEqualTo("Test payment");
                }));
        }

        @ParameterizedTest
        @MethodSource("invalidRequests")
        @DisplayName("should return 400 for invalid request")
        void shouldReturn400ForInvalidRequest(CreateTransactionRequest request) throws Exception {
            postTransaction(UuidFactory.generate(), request).andExpect(status().isBadRequest());

            verify(createTransactionHandler, never()).createTransaction(any());
        }

        @Test
        @DisplayName("should return 400 when request body is missing")
        void shouldReturn400WhenRequestBodyMissing() throws Exception {
            mockMvc.perform(post(TRANSACTIONS_PATH, UuidFactory.generate()).contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());

            verify(createTransactionHandler, never()).createTransaction(any());
        }

        static Stream<Arguments> invalidRequests() {
            var id = UuidFactory.generate();
            return Stream.of(Arguments.of(new CreateTransactionRequest(id, null, "EUR", TransactionDirection.OUT, "Test")),
                Arguments.of(new CreateTransactionRequest(id, new BigDecimal("100"), "", TransactionDirection.OUT, "Test")),
                Arguments.of(new CreateTransactionRequest(id, new BigDecimal("100"), "EUR", null, "Test")),
                Arguments.of(new CreateTransactionRequest(id, new BigDecimal("100"), "EUR", TransactionDirection.OUT, ""))
            );
        }
    }

    @Nested
    @DisplayName("GET " + TRANSACTIONS_PATH)
    class ListTransactionsTests {

        @Test
        @DisplayName("should list transactions for an account")
        void shouldListTransactions() throws Exception {
            var accountId = UuidFactory.generate();
            var expected = List.of(
                new TransactionResponse(
                    accountId,
                    UuidFactory.generate(),
                    new BigDecimal("100"),
                    "USD",
                    TransactionDirection.IN,
                    "Deposit",
                    new BigDecimal("100")
                ),
                new TransactionResponse(
                    accountId,
                    UuidFactory.generate(),
                    new BigDecimal("50"),
                    "USD",
                    TransactionDirection.OUT,
                    "Withdrawal",
                    new BigDecimal("50")
                )
            );

            given(getTransactionsHandler.handle(any(GetTransactionsQuery.class))).willReturn(expected);

            getTransactions(accountId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[1].description").value("Withdrawal"));

            verify(getTransactionsHandler).handle(assertArg(q -> assertThat(q.accountId()).isEqualTo(accountId)));
        }

        @Test
        @DisplayName("should return empty list when no transactions exist")
        void shouldReturnEmptyListWhenNoTransactions() throws Exception {
            var accountId = UuidFactory.generate();

            given(getTransactionsHandler.handle(any(GetTransactionsQuery.class))).willReturn(List.of());

            getTransactions(accountId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    private ResultActions postAccount(CreateAccountRequest request) throws Exception {
        return mockMvc.perform(post(ACCOUNTS_PATH)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions getAccount(UUID accountId) throws Exception {
        return mockMvc.perform(get(ACCOUNT_PATH, accountId));
    }

    private ResultActions postTransaction(UUID accountId, CreateTransactionRequest request) throws Exception {
        return mockMvc.perform(post(TRANSACTIONS_PATH, accountId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions getTransactions(UUID accountId) throws Exception {
        return mockMvc.perform(get(TRANSACTIONS_PATH, accountId));
    }
}
