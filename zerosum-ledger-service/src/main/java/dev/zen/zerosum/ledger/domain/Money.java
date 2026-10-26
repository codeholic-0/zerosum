package dev.zen.zerosum.ledger.domain;

import java.util.Currency;
import java.util.Objects;

/** An amount in minor units (e.g. cents) of one ISO 4217 currency. Never floating point. */
public record Money(long amountMinor, Currency currency) {
    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long amountMinor, String currencyCode) {
        Objects.requireNonNull(currencyCode);

        try {
            return new Money(amountMinor, Currency.getInstance(currencyCode));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid currency code: " + currencyCode, e);
        }
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(Money amount) {
        chkCurrency(amount);
        return new Money(Math.addExact(amountMinor, amount.amountMinor), currency);
    }

    public Money minus(Money amount) {
        chkCurrency(amount);
        return new Money(Math.subtractExact(amountMinor, amount.amountMinor), currency);
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    private void chkCurrency(Money amount) {
        if (!currency.equals(amount.currency)) {
            throw new IllegalArgumentException(
                    "Currency Mismatch! " + currency + " vs " + amount.currency);
        }
    }
}
