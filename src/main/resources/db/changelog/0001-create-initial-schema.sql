CREATE TABLE accounts
(
    id          UUID PRIMARY KEY NOT NULL,
    customer_id UUID             NOT NULL,
    country     VARCHAR(3)       NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);

CREATE TABLE balances
(
    account_id       UUID           NOT NULL REFERENCES accounts (id),
    currency         VARCHAR(3)     NOT NULL,
    available_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, currency)
);

CREATE TYPE transaction_direction AS ENUM ('IN', 'OUT');

CREATE TABLE transactions
(
    id            UUID PRIMARY KEY      NOT NULL,
    account_id    UUID                  NOT NULL REFERENCES accounts (id),
    amount        NUMERIC(19, 2)        NOT NULL,
    currency      VARCHAR(3)            NOT NULL,
    direction     transaction_direction NOT NULL,
    description   TEXT                  NOT NULL,
    balance_after NUMERIC(19, 2)        NOT NULL,
    created_at    TIMESTAMPTZ           NOT NULL DEFAULT NOW(),
    CONSTRAINT transactions_amount_chk CHECK (amount > 0),
    CONSTRAINT transactions_description_chk CHECK (length(trim(description)) > 0)
);

CREATE INDEX idx_transactions_account_id_created_at ON transactions (account_id, created_at DESC);

CREATE INDEX idx_transactions_account_id_currency_created_at ON transactions (account_id, currency, created_at DESC);

