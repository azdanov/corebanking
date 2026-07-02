package dev.azdanov.corebanking.shared.money;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;

public final class MoneyFactory {

    private MoneyFactory() {
    }

    public static Money of(String currencyCode, BigDecimal amount) {
        CurrencyUnit unit = CurrencyFactory.of(currencyCode);
        return Money.of(unit, amount);
    }

    public static Money zero(String currencyCode) {
        CurrencyUnit unit = CurrencyFactory.of(currencyCode);
        return Money.zero(unit);
    }
}
