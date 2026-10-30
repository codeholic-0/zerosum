package dev.zen.zerosum.ledger.api;

import dev.zen.zerosum.ledger.exception.LedgerException;
import java.util.Currency;

public final class Currencies {
    private Currencies() {}

    public static Currency parse(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException ex) {
            throw new LedgerException.InvalidInput("Unknown Currency: " + code);
        }
    }
}
