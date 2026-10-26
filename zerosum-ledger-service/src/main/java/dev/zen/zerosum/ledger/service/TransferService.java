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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final JournalEntryRepository entries;
    private final EntityManager entityManager;

    @Transactional
    public PostedTransfer post(PostTransferCommand command) {
        validateShape(command);
        validateAccounts(command);

        Transfer transfer =
                transfers.save(
                        Transfer.create(
                                command.reference(), command.description(), command.currency()));
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

        return new PostedTransfer(transfer, posted);
    }

    private void validateShape(PostTransferCommand command) {
        List<PostTransferCommand.Leg> legs = command.legs();

        if (legs.size() < 2) {
            throw new LedgerException.InvalidTransfer("A transfer needs at least two ledgers");
        }

        Money debits = Money.zero(command.currency());
        Money credits = Money.zero(command.currency());

        Set<UUID> debitAccounts = new HashSet<>();
        Set<UUID> creditAccounts = new HashSet<>();

        try {
            for (PostTransferCommand.Leg leg : legs) {
                if (!leg.amount().isPositive()) {
                    throw new LedgerException.InvalidTransfer(
                            "Leger amount must be positive: " + leg.amount());
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
        } catch (ArithmeticException ex) {
            throw new LedgerException.InvalidTransfer("Transfer exceeds supported range");
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

    private void validateAccounts(PostTransferCommand command) {
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
    }
}
