package dev.zen.zerosum.ledger.service;

import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.repositories.AccountRepository;
import dev.zen.zerosum.ledger.repositories.CurrencyTotal;
import dev.zen.zerosum.ledger.repositories.JournalEntryRepository;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;

    /** Balance = credits minus debits, derived from journal entries */
    @Transactional(readOnly = true)
    public Money balanceOf(UUID accountId) {
        var account =
                accounts.findById(accountId)
                        .orElseThrow(
                                () ->
                                        new LedgerException.AccountNotFound(
                                                "Unknown account: " + accountId));
        return new Money(entries.balanceMinor(accountId), account.getCurrency());
    }

    /** Integrity check: in a healthy ledger every currency nets to exactly zero. */
    @Transactional(readOnly = true)
    public Map<Currency, Long> trialBalance() {
        Map<Currency, Long> result = new LinkedHashMap<>();
        for (CurrencyTotal total : entries.totalsByCurrency()) {
            result.put(Currency.getInstance(total.getCurrency()), total.getTotal());
        }
        return result;
    }
}
