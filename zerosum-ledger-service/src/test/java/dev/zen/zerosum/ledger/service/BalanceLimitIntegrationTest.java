package dev.zen.zerosum.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.repositories.JournalEntryRepository;
import dev.zen.zerosum.ledger.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class BalanceLimitIntegrationTest extends AbstractIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired TransferService transferService;
    @Autowired BalanceService balanceService;
    @Autowired JournalEntryRepository entries;
    @Autowired DataSource dataSource;

    private Account bank; // unconstrained
    private Account wallet; // constrained: balance may not go below 0
    private Account other; // unconstrained

    @BeforeEach
    void createAccounts() {
        bank = newAccount("BANK", "USD", AccountType.ASSET);
        wallet = newLimitedAccount("WALLET", "USD", AccountType.LIABILITY, 0);
        other = newAccount("OTHER", "USD", AccountType.REVENUE);
    }

    @Test
    void aConstrainedAccountGetsABalanceRowAndAnUnconstrainedOneDoesNot() {
        assertThat(count("account_balances")).isEqualTo(1);
        assertThat(storedBalance(wallet)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select min_balance_minor from account_balances where account_id = ?",
                                Long.class,
                                wallet.getId()))
                .isZero();
    }

    @Test
    void unconstrainedAccountsNeverGetABalanceRowNoMatterHowBusy() {
        move(bank, wallet, 1000);
        move(wallet, other, 100);

        assertThat(count("account_balances")).isEqualTo(1);
    }

    @Test
    void depositsAndWithdrawalsMoveTheMaintainedBalance() {
        move(bank, wallet, 5000);
        move(wallet, other, 1200);

        assertThat(storedBalance(wallet)).isEqualTo(3800);
        assertThat(balanceService.balanceOf(wallet.getId())).isEqualTo(Money.of(3800, "USD"));
    }

    @Test
    void aTransferThatCrossesTheLimitIsRejectedAndWritesNothing() {
        move(bank, wallet, 100);
        long transfersBefore = count("transfers");
        long entriesBefore = count("journal_entries");

        assertThatThrownBy(() -> move(wallet, other, 101))
                .isInstanceOf(LedgerException.InsufficientFunds.class);

        assertThat(count("transfers")).isEqualTo(transfersBefore);
        assertThat(count("journal_entries")).isEqualTo(entriesBefore);
        assertThat(storedBalance(wallet)).isEqualTo(100);
    }

    @Test
    void landingExactlyOnTheLimitIsAllowed() {
        move(bank, wallet, 100);

        move(wallet, other, 100);

        assertThat(storedBalance(wallet)).isZero();
    }

    @Test
    void aNegativeLimitAllowsAnOverdraftUpToThatLimit() {
        Account overdraft = newLimitedAccount("OVERDRAFT", "USD", AccountType.ASSET, -1000);

        move(overdraft, other, 1000);
        assertThat(storedBalance(overdraft)).isEqualTo(-1000);

        assertThatThrownBy(() -> move(overdraft, other, 1))
                .isInstanceOf(LedgerException.InsufficientFunds.class);
        assertThat(storedBalance(overdraft)).isEqualTo(-1000);
    }

    @Test
    void theLimitAppliesToTheCombinedChangeOfAllLegsOnAnAccount() {
        move(bank, wallet, 100);
        Account other2 = newAccount("OTHER2", "USD", AccountType.REVENUE);

        // Each debit leg alone (60) fits in the 100 balance; together (120) they do not.
        PostTransferCommand command =
                new PostTransferCommand(
                        "ref",
                        "test",
                        USD,
                        List.of(
                                PostTransferCommand.debit(wallet.getId(), Money.of(60, "USD")),
                                PostTransferCommand.debit(wallet.getId(), Money.of(60, "USD")),
                                PostTransferCommand.credit(other.getId(), Money.of(60, "USD")),
                                PostTransferCommand.credit(other2.getId(), Money.of(60, "USD"))));

        assertThatThrownBy(() -> transferService.post(command))
                .isInstanceOf(LedgerException.InsufficientFunds.class);
        assertThat(storedBalance(wallet)).isEqualTo(100);
    }

    @Test
    void theMaintainedBalanceAlwaysEqualsTheBalanceDerivedFromEntries() {
        move(bank, wallet, 5000);
        move(wallet, other, 700);
        move(bank, wallet, 250);
        move(wallet, other, 3000);

        assertThat(storedBalance(wallet))
                .isEqualTo(1550)
                .isEqualTo(entries.balanceMinor(wallet.getId()));
    }

    @Test
    void theDatabaseItselfRefusesToCrossTheLimit() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update account_balances set balance_minor = -1"
                                                + " where account_id = ?",
                                        wallet.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(storedBalance(wallet)).isZero();
    }

    @Test
    void theDatabaseRefusesAPositiveLimit() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update account_balances set min_balance_minor = 5"
                                                + " where account_id = ?",
                                        wallet.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unconstrainedAccountsAreNeverLockedButConstrainedOnesAre() throws Exception {
        move(bank, wallet, 1000);
        Account a = newAccount("A", "USD", AccountType.ASSET);
        Account b = newAccount("B", "USD", AccountType.REVENUE);

        try (Connection lockHolder = dataSource.getConnection();
                ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            // Hold a row lock on the wallet's balance row in an open transaction.
            lockHolder.setAutoCommit(false);
            try (var statement = lockHolder.createStatement()) {
                statement.execute(
                        "update account_balances set balance_minor = balance_minor"
                                + " where account_id = '"
                                + wallet.getId()
                                + "'");
            }

            // 1) A transfer between unconstrained accounts is not held up by that lock.
            Future<?> free = pool.submit(() -> move(a, b, 10));
            free.get(10, TimeUnit.SECONDS); // a timeout here would fail the test

            // 2) A transfer touching the constrained account waits for the lock.
            Future<?> blocked = pool.submit(() -> move(wallet, other, 10));
            waitUntilSomeoneWaitsOnALock();
            assertThat(blocked.isDone()).isFalse();

            lockHolder.rollback();
            blocked.get(10, TimeUnit.SECONDS);
        }

        assertThat(storedBalance(wallet)).isEqualTo(990);
    }

    private void waitUntilSomeoneWaitsOnALock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long waiting =
                    jdbc.queryForObject(
                            "select count(*) from pg_stat_activity"
                                    + " where wait_event_type = 'Lock'"
                                    + " and datname = current_database()",
                            Long.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no transaction ever waited on a lock");
    }

    private long storedBalance(Account account) {
        return jdbc.queryForObject(
                "select balance_minor from account_balances where account_id = ?",
                Long.class,
                account.getId());
    }

    /** Debits one account and credits another by the same amount. */
    private void move(Account debit, Account credit, long amount) {
        transferService.post(
                new PostTransferCommand(
                        "ref",
                        "test",
                        USD,
                        List.of(
                                PostTransferCommand.debit(debit.getId(), Money.of(amount, "USD")),
                                PostTransferCommand.credit(
                                        credit.getId(), Money.of(amount, "USD")))));
    }
}
