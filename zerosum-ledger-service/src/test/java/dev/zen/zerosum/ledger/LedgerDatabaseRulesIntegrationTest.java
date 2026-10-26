package dev.zen.zerosum.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.service.PostTransferCommand;
import dev.zen.zerosum.ledger.service.TransferService;
import dev.zen.zerosum.ledger.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class LedgerDatabaseRulesIntegrationTest extends AbstractIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired TransferService transferService;

    private Account cash;
    private Account sales;

    @BeforeEach
    void createAccounts() {
        cash = newAccount("CASH", "USD", AccountType.ASSET);
        sales = newAccount("SALES", "USD", AccountType.REVENUE);
    }

    @Test
    void anUnbalancedTransferCannotBeCommitted() throws Exception {
        UUID transferId = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                insertTransfer(s, transferId);
                insertEntry(s, transferId, cash.getId(), "DEBIT", 100);
            }
            assertThatThrownBy(c::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("unbalanced");
            c.rollback();
        }

        assertThat(count("transfers")).isZero();
        assertThat(count("journal_entries")).isZero();
    }

    @Test
    void aBalancedTransferCommits() throws Exception {
        UUID transferId = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                insertTransfer(s, transferId);
                insertEntry(s, transferId, cash.getId(), "DEBIT", 100);
                insertEntry(s, transferId, sales.getId(), "CREDIT", 100);
            }
            c.commit();
        }

        assertThat(count("journal_entries")).isEqualTo(2);
    }

    @Test
    void entriesAndTransfersCannotBeUpdatedOrDeleted() {
        transferService.post(
                new PostTransferCommand(
                        "ref",
                        "test",
                        Currency.getInstance("USD"),
                        List.of(
                                PostTransferCommand.debit(cash.getId(), Money.of(5000, "USD")),
                                PostTransferCommand.credit(sales.getId(), Money.of(5000, "USD")))));

        assertThatThrownBy(() -> jdbc.update("update journal_entries set amount_minor = 1"))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from journal_entries"))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update transfers set description = 'x'"))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from transfers"))
                .hasStackTraceContaining("append-only");

        assertThat(count("journal_entries")).isEqualTo(2);
        assertThat(count("transfers")).isEqualTo(1);
    }

    @Test
    void anEntryCannotUseADifferentCurrencyThanItsAccount() {
        UUID transferId = UUID.randomUUID();
        jdbc.update("insert into transfers (id, currency) values (?, 'EUR')", transferId);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
                    insert into journal_entries
                        (transfer_id, account_id, direction, amount_minor, currency)
                    values (?, ?, 'DEBIT', 100, 'EUR')
                    """,
                                        transferId,
                                        cash.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anEntryAmountMustBePositive() {
        UUID transferId = UUID.randomUUID();
        jdbc.update("insert into transfers (id, currency) values (?, 'USD')", transferId);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
                    insert into journal_entries
                        (transfer_id, account_id, direction, amount_minor, currency)
                    values (?, ?, 'DEBIT', 0, 'USD')
                    """,
                                        transferId,
                                        cash.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertTransfer(Statement s, UUID id) throws SQLException {
        s.execute("insert into transfers (id, currency) values ('" + id + "', 'USD')");
    }

    private static void insertEntry(
            Statement s, UUID transferId, UUID accountId, String direction, long amount)
            throws SQLException {
        s.execute(
                ("insert into journal_entries (transfer_id, account_id, direction, amount_minor,"
                                + " currency) values ('%s', '%s', '%s', %d, 'USD')")
                        .formatted(transferId, accountId, direction, amount));
    }
}
