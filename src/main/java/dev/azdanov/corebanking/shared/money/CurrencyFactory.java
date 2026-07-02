package dev.azdanov.corebanking.shared.money;

import org.joda.money.CurrencyUnit;
import org.joda.money.IllegalCurrencyException;

import java.util.Set;

import static java.util.stream.Collectors.joining;

public final class CurrencyFactory {

    private static final Set<CurrencyUnit> ALLOWED = Set.of(
        CurrencyUnit.EUR,
        CurrencyUnit.GBP,
        CurrencyUnit.USD,
        CurrencyUnit.of("SEK")
    );

    private CurrencyFactory() {
    }

    public static CurrencyUnit of(String currencyCode) {
        CurrencyUnit unit;
        try {
            unit = CurrencyUnit.of(currencyCode);
        } catch (IllegalCurrencyException e) {
            throw new IllegalArgumentException("Invalid currency: " + currencyCode, e);
        }
        if (!ALLOWED.contains(unit)) {
            var allowedCodes = ALLOWED.stream().map(CurrencyUnit::getCode).collect(joining(", "));
            throw new IllegalArgumentException(
                "Currency " + currencyCode + " is not supported. Allowed: " + allowedCodes);
        }
        return unit;
    }
}
