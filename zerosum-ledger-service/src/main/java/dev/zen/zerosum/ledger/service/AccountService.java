package dev.zen.zerosum.ledger.service;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.repositories.AccountRepository;
import java.util.Currency;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accounts;
    private final JdbcClient jdbc;

    @Transactional
    public Account open(String code, String name, Currency currency, AccountType type) {
        return open(code, name, currency, type, null);
    }

    /** minBalanceMinor null means unconstrained; otherwise the account gets a balance row. */
    @Transactional
    public Account open(
            String code, String name, Currency currency, AccountType type, Long minBalanceMinor) {
        Account account;
        try {
            // Flush so a unique-constraint violation surfaces here, not at commit.
            account =
                    accounts.saveAndFlush(
                            Account.open(code, name, currency, type, minBalanceMinor));
        } catch (DataIntegrityViolationException e) {
            // The only constraint a valid request can break is the unique account code.
            throw new LedgerException.DuplicateAccount(code);
        }
        if (minBalanceMinor != null) {
            jdbc.sql(
                            """
                    insert into account_balances (account_id, balance_minor, min_balance_minor)
                    values (:id, 0, :min)
                    """)
                    .param("id", account.getId())
                    .param("min", minBalanceMinor)
                    .update();
        }
        return account;
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new LedgerException.AccountNotFound("Unknown account: " + id));
    }
}
