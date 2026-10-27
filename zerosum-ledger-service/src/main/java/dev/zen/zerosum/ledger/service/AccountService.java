package dev.zen.zerosum.ledger.service;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.repositories.AccountRepository;
import java.util.Currency;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accounts;

    @Transactional
    public Account open(String code, String name, Currency currency, AccountType type) {
        try {
            // Flush, so a unique-constraint violation surfaces here, not at commit.
            return accounts.saveAndFlush(Account.open(code, name, currency, type));
        } catch (DataIntegrityViolationException e) {
            // The only constraint a valid request can break is the unique account code
            throw new LedgerException.DuplicateAccount(code);
        }
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new LedgerException.AccountNotFound("Unknown account: " + id));
    }
}
