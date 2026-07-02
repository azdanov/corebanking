package dev.azdanov.corebanking.account.domain.account;

import org.joda.money.Money;

public class Balance {
    private final AccountId accountId;
    private final String currency;
    private Money availableAmount;

    public Balance(AccountId accountId, String currency, Money availableAmount) {
        this.accountId = accountId;
        this.currency = currency;
        this.availableAmount = availableAmount;
    }

    public AccountId accountId() {
        return accountId;
    }

    public String currency() {
        return currency;
    }

    public Money availableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(Money availableAmount) {
        this.availableAmount = availableAmount;
    }
}
