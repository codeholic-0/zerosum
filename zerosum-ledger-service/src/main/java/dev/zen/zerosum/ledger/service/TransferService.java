package dev.zen.zerosum.ledger.service;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.domain.JournalEntry;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.domain.Transfer;
import dev.zen.zerosum.ledger.enums.Direction;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.repositories.AccountRepository;
import dev.zen.zerosum.ledger.repositories.JournalEntryRepository;
import dev.zen.zerosum.ledger.repositories.TransferRepository;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final JournalEntryRepository entries;
    private final EntityManager entityManager;
    private final JdbcClient jdbc;

    @Transactional
    public PostedTransfer post(PostTransferCommand command) {
        validateShape(command);
        Map<UUID, Account> involved = loadAndValidateAccounts(command);

        Transfer transfer =
                transfers.save(
                        Transfer.create(
                                command.reference(), command.description(), command.currency()));
        // Make sure the parent row is inserted before its entries (foreign key).
        entityManager.flush();

        List<JournalEntry> posted =
                entries.saveAll(
                        command.legs().stream()
                                .map(
                                        leg ->
                                                JournalEntry.of(
                                                        transfer.getId(),
                                                        leg.accountId(),
                                                        leg.direction(),
                                                        leg.amount()))
                                .toList());

        // Last on purpose: balance rows are the only contended rows, and a row lock is held until
        // commit, so take them as late as possible.
        applyBalanceLimits(command, involved);
        return new PostedTransfer(transfer, posted);
    }

    @Transactional(readOnly = true)
    public PostedTransfer get(UUID id) {
        Transfer transfer =
                transfers.findById(id).orElseThrow(() -> new LedgerException.TransferNotFound(id));
        return new PostedTransfer(transfer, entries.findByTransferIdOrderByIdAsc(id));
    }

    /**
     * Applies each constrained account's net change as ONE atomic conditional update. There is no
     * read-then-write, so concurrent transfers cannot both pass a check they would jointly break.
     * Accounts are processed in ascending id order so two transfers can never lock the same rows in
     * opposite orders (deadlock avoidance). Unconstrained accounts are never touched.
     */
    private void applyBalanceLimits(PostTransferCommand command, Map<UUID, Account> involved) {
        Map<UUID, Long> deltas = new TreeMap<>();
        for (PostTransferCommand.Leg leg : command.legs()) {
            if (involved.get(leg.accountId()).getMinBalanceMinor() == null) {
                continue;
            }
            deltas.merge(
                    leg.accountId(),
                    leg.direction().sign() * leg.amount().amountMinor(),
                    Long::sum);
        }

        for (Map.Entry<UUID, Long> change : deltas.entrySet()) {
            int updated =
                    jdbc.sql(
                                    """
                        update account_balances
                           set balance_minor = balance_minor + :delta
                         where account_id = :id
                           and balance_minor + :delta >= min_balance_minor
                        """)
                            .param("delta", change.getValue())
                            .param("id", change.getKey())
                            .update();
            if (updated == 0) {
                // The row exists (created with the account), so zero rows means the limit.
                throw new LedgerException.InsufficientFunds(
                        involved.get(change.getKey()).getCode());
            }
        }
    }

    private void validateShape(PostTransferCommand command) {
        List<PostTransferCommand.Leg> legs = command.legs();
        if (legs.size() < 2) {
            throw new LedgerException.InvalidTransfer("A transfer needs at least two legs");
        }

        Money debits = Money.zero(command.currency());
        Money credits = Money.zero(command.currency());
        Set<UUID> debitAccounts = new HashSet<>();
        Set<UUID> creditAccounts = new HashSet<>();

        try {
            for (PostTransferCommand.Leg leg : legs) {
                if (!leg.amount().isPositive()) {
                    throw new LedgerException.InvalidTransfer(
                            "Leg amount must be positive: " + leg.amount());
                }
                if (!leg.amount().currency().equals(command.currency())) {
                    throw new LedgerException.CurrencyMismatch(
                            "Leg currency "
                                    + leg.amount().currency()
                                    + " differs from transfer currency "
                                    + command.currency());
                }
                if (leg.direction() == Direction.DEBIT) {
                    debits = debits.plus(leg.amount());
                    debitAccounts.add(leg.accountId());
                } else {
                    credits = credits.plus(leg.amount());
                    creditAccounts.add(leg.accountId());
                }
            }
        } catch (ArithmeticException e) {
            throw new LedgerException.InvalidTransfer("Transfer total exceeds supported range");
        }

        if (!debits.equals(credits)) {
            throw new LedgerException.UnbalancedTransfer(debits, credits);
        }

        Set<UUID> both = new HashSet<>(debitAccounts);
        both.retainAll(creditAccounts);
        if (!both.isEmpty()) {
            throw new LedgerException.InvalidTransfer(
                    "An account cannot be on both sides of one transfer: " + both);
        }
    }

    private Map<UUID, Account> loadAndValidateAccounts(PostTransferCommand command) {
        Set<UUID> ids =
                command.legs().stream()
                        .map(PostTransferCommand.Leg::accountId)
                        .collect(Collectors.toSet());

        Map<UUID, Account> found =
                accounts.findAllById(ids).stream()
                        .collect(Collectors.toMap(Account::getId, Function.identity()));

        Set<UUID> missing = new HashSet<>(ids);
        missing.removeAll(found.keySet());
        if (!missing.isEmpty()) {
            throw new LedgerException.AccountNotFound("Unknown accounts: " + missing);
        }

        for (Account account : found.values()) {
            if (!account.getCurrency().equals(command.currency())) {
                throw new LedgerException.CurrencyMismatch(
                        "Account "
                                + account.getCode()
                                + " is in "
                                + account.getCurrency()
                                + " but the transfer is in "
                                + command.currency());
            }
        }
        return found;
    }
}
