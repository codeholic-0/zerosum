package dev.zen.zerosum.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.domain.JournalEntry;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.support.AbstractIntegrationTest;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TransferServiceIntegrationTest extends AbstractIntegrationTest {
    private static final Currency USD = Currency.getInstance("USD");

    @Autowired TransferService transferService;

    private Account cash;
    private Account sales;

    @BeforeEach
    void createAccounts() {
        cash = newAccount("CASH", "USD", AccountType.ASSET);
        sales = newAccount("SALES", "USD", AccountType.REVENUE);
    }

    @Test
    void postsABalancedTransferWithOneEntryPerLeg() {
        PostedTransfer posted =
                transferService.post(
                        command(
                                USD,
                                PostTransferCommand.debit(cash.getId(), usd(5000)),
                                PostTransferCommand.credit(sales.getId(), usd(5000))));

        assertThat(posted.entries()).hasSize(2);
        assertThat(count("transfers")).isEqualTo(1);
        assertThat(count("journal_entries")).isEqualTo(2);
        assertThat(posted.entries())
                .extracting(JournalEntry::signedAmountMinor)
                .containsExactlyInAnyOrder(-5000L, 5000L);
    }

    @Test
    void rejectsFewerThanTwoLegs() {
        assertRejected(
                LedgerException.InvalidTransfer.class,
                command(USD, PostTransferCommand.debit(cash.getId(), usd(100))));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertRejected(
                LedgerException.InvalidTransfer.class,
                command(
                        USD,
                        PostTransferCommand.debit(cash.getId(), usd(0)),
                        PostTransferCommand.credit(sales.getId(), usd(0))));
    }

    @Test
    void rejectsUnbalancedTransfers() {
        assertRejected(
                LedgerException.UnbalancedTransfer.class,
                command(
                        USD,
                        PostTransferCommand.debit(cash.getId(), usd(5000)),
                        PostTransferCommand.credit(sales.getId(), usd(4000))));
    }

    @Test
    void rejectsALegInADifferentCurrencyThanTheTransfer() {
        assertRejected(
                LedgerException.CurrencyMismatch.class,
                command(
                        USD,
                        PostTransferCommand.debit(cash.getId(), usd(100)),
                        PostTransferCommand.credit(sales.getId(), Money.of(100, "EUR"))));
    }

    @Test
    void rejectsTheSameAccountOnBothSides() {
        assertRejected(
                LedgerException.InvalidTransfer.class,
                command(
                        USD,
                        PostTransferCommand.debit(cash.getId(), usd(100)),
                        PostTransferCommand.credit(cash.getId(), usd(100))));
    }

    @Test
    void rejectsUnknownAccounts() {
        assertRejected(
                LedgerException.AccountNotFound.class,
                command(
                        USD,
                        PostTransferCommand.debit(UUID.randomUUID(), usd(100)),
                        PostTransferCommand.credit(sales.getId(), usd(100))));
    }

    @Test
    void rejectsAnAccountWhoseCurrencyDiffersFromTheTransfer() {
        Account euroCash = newAccount("EUR-CASH", "EUR", AccountType.ASSET);

        assertRejected(
                LedgerException.CurrencyMismatch.class,
                command(
                        USD,
                        PostTransferCommand.debit(euroCash.getId(), usd(100)),
                        PostTransferCommand.credit(sales.getId(), usd(100))));
    }

    @Test
    void aFailureWhileWritingEntriesRollsBackTheWholeTransfer() {
        // Test-only trigger: makes any entry of exactly 999 minor units fail on insert.
        jdbc.execute(
                """
            create function test_fail_on_999() returns trigger as $$
            begin
                if new.amount_minor = 999 then
                    raise exception 'simulated failure';
                end if;
                return new;
            end;
            $$ language plpgsql
            """);
        jdbc.execute(
                """
            create trigger trg_test_fail before insert on journal_entries
            for each row execute function test_fail_on_999()
            """);
        try {
            assertThatThrownBy(
                            () ->
                                    transferService.post(
                                            command(
                                                    USD,
                                                    PostTransferCommand.debit(
                                                            cash.getId(), usd(999)),
                                                    PostTransferCommand.credit(
                                                            sales.getId(), usd(999)))))
                    .hasStackTraceContaining("simulated failure");

            assertThat(count("transfers")).isZero();
            assertThat(count("journal_entries")).isZero();
        } finally {
            jdbc.execute("drop trigger trg_test_fail on journal_entries");
            jdbc.execute("drop function test_fail_on_999()");
        }
    }

    private void assertRejected(Class<? extends LedgerException> type, PostTransferCommand cmd) {
        assertThatThrownBy(() -> transferService.post(cmd)).isInstanceOf(type);
        assertThat(count("transfers")).isZero();
        assertThat(count("journal_entries")).isZero();
    }

    private static Money usd(long minor) {
        return Money.of(minor, "USD");
    }

    private static PostTransferCommand command(Currency currency, PostTransferCommand.Leg... legs) {
        return new PostTransferCommand("ref", "test", currency, List.of(legs));
    }
}
