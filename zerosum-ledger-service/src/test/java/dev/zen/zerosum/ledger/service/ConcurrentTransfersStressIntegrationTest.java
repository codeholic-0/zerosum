package dev.zen.zerosum.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.repositories.JournalEntryRepository;
import dev.zen.zerosum.ledger.support.AbstractIntegrationTest;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=20")
class ConcurrentTransfersStressIntegrationTest extends AbstractIntegrationTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final int TRANSFERS = Integer.getInteger("zerosum.stress.transfers", 2000);
    private static final int CONCURRENCY = Integer.getInteger("zerosum.stress.concurrency", 16);
    private static final long SEED = Long.getLong("zerosum.seed", 20261027L);
    private static final int WALLETS = 4;
    private static final long INITIAL_FUNDING = 10_000;

    private enum Kind {
        DEPOSIT,
        WALLET_TO_WALLET,
        WALLET_TO_FEES
    }

    private record Plan(Kind kind, int from, int to, long amount) {}

    @Autowired TransferService transferService;
    @Autowired BalanceService balanceService;
    @Autowired JournalEntryRepository entries;

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    int poolSize;

    // Per-test state (JUnit creates a new instance per test method).
    private Account bank;
    private Account fees;
    private final Account[] wallets = new Account[WALLETS];
    private final AtomicLongArray expectedWallet = new AtomicLongArray(WALLETS);
    private final AtomicLong expectedBank = new AtomicLong();
    private final AtomicLong expectedFees = new AtomicLong();
    private final AtomicInteger committed = new AtomicInteger();
    private final AtomicInteger rejected = new AtomicInteger();
    private final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

    @Test
    void heavyParallelLoadOnAFewHotAccountsNeverBreaksALimitOrLosesAnUpdate() throws Exception {
        bank = newAccount("BANK", "USD", AccountType.ASSET);
        fees = newAccount("FEES", "USD", AccountType.REVENUE);
        for (int i = 0; i < WALLETS; i++) {
            wallets[i] = newLimitedAccount("WALLET-" + i, "USD", AccountType.LIABILITY, 0);
            post(bank, wallets[i], INITIAL_FUNDING);
            expectedWallet.set(i, INITIAL_FUNDING);
            expectedBank.addAndGet(-INITIAL_FUNDING);
        }

        List<Plan> plans = plans(new SplittableRandom(SEED));
        Semaphore inFlight = new Semaphore(CONCURRENCY);

        long started = System.nanoTime();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Plan plan : plans) {
                inFlight.acquire();
                pool.submit(
                        () -> {
                            try {
                                execute(plan);
                            } finally {
                                inFlight.release();
                            }
                        });
            }
        } // close() waits for every task to finish
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        report(elapsedMillis);

        // 1. Nothing went wrong except the expected business rejections (no deadlocks, no
        //    timeouts, no SQL errors).
        if (!failures.isEmpty()) {
            throw new AssertionError(
                    failures.size() + " unexpected failures; the first one:", failures.peek());
        }
        assertThat(committed.get() + rejected.get()).isEqualTo(TRANSFERS);
        assertThat(rejected.get()).as("the limits must actually have been hit").isPositive();
        assertThat(committed.get()).isPositive();
        assertThat(count("transfers")).isEqualTo(WALLETS + committed.get());

        // 2. No limit violated, no lost update: the maintained balance, the balance derived from
        //    entries and the test's own model all agree.
        for (int i = 0; i < WALLETS; i++) {
            long maintained = balanceService.balanceOf(wallets[i].getId()).amountMinor();
            long derived = entries.balanceMinor(wallets[i].getId());
            assertThat(maintained)
                    .as(wallets[i].getCode())
                    .isGreaterThanOrEqualTo(0)
                    .isEqualTo(expectedWallet.get(i))
                    .isEqualTo(derived);
        }

        // 3. The hot unconstrained accounts agree with the model too, and were never locked or
        //    given a balance row.
        assertThat(balanceService.balanceOf(fees.getId()).amountMinor())
                .isEqualTo(expectedFees.get());
        assertThat(balanceService.balanceOf(bank.getId()).amountMinor())
                .isEqualTo(expectedBank.get());
        assertThat(count("account_balances")).isEqualTo(WALLETS);

        // 4. The whole ledger still nets to zero.
        assertThat(balanceService.trialBalance())
                .allSatisfy((currency, total) -> assertThat(total).isZero());
    }

    private void execute(Plan plan) {
        Account debit;
        Account credit;
        if (plan.kind() == Kind.DEPOSIT) {
            debit = bank;
            credit = wallets[plan.to()];
        } else if (plan.kind() == Kind.WALLET_TO_WALLET) {
            debit = wallets[plan.from()];
            credit = wallets[plan.to()];
        } else {
            debit = wallets[plan.from()];
            credit = fees;
        }

        try {
            post(debit, credit, plan.amount());
            applyToModel(plan);
            committed.incrementAndGet();
        } catch (LedgerException.InsufficientFunds e) {
            rejected.incrementAndGet();
        } catch (Throwable t) {
            failures.add(t);
        }
    }

    private void applyToModel(Plan plan) {
        long amount = plan.amount();
        switch (plan.kind()) {
            case DEPOSIT -> {
                expectedBank.addAndGet(-amount);
                expectedWallet.addAndGet(plan.to(), amount);
            }
            case WALLET_TO_WALLET -> {
                expectedWallet.addAndGet(plan.from(), -amount);
                expectedWallet.addAndGet(plan.to(), amount);
            }
            case WALLET_TO_FEES -> {
                expectedWallet.addAndGet(plan.from(), -amount);
                expectedFees.addAndGet(amount);
            }
        }
    }

    /** Mostly withdrawals, so the wallets drain and the limits really bind. */
    private static List<Plan> plans(SplittableRandom random) {
        List<Plan> plans = new ArrayList<>(TRANSFERS);
        for (int i = 0; i < TRANSFERS; i++) {
            int roll = random.nextInt(100);
            if (roll < 20) {
                plans.add(
                        new Plan(
                                Kind.DEPOSIT,
                                -1,
                                random.nextInt(WALLETS),
                                1 + random.nextLong(1000)));
            } else if (roll < 40) {
                int from = random.nextInt(WALLETS);
                int to = (from + 1 + random.nextInt(WALLETS - 1)) % WALLETS;
                plans.add(new Plan(Kind.WALLET_TO_WALLET, from, to, 1 + random.nextLong(3000)));
            } else {
                plans.add(
                        new Plan(
                                Kind.WALLET_TO_FEES,
                                random.nextInt(WALLETS),
                                -1,
                                1 + random.nextLong(1500)));
            }
        }
        return plans;
    }

    private void post(Account debit, Account credit, long amount) {
        transferService.post(
                new PostTransferCommand(
                        "stress",
                        "stress",
                        USD,
                        List.of(
                                PostTransferCommand.debit(debit.getId(), Money.of(amount, "USD")),
                                PostTransferCommand.credit(
                                        credit.getId(), Money.of(amount, "USD")))));
    }

    private void report(long elapsedMillis) {
        double seconds = Math.max(elapsedMillis, 1) / 1000.0;
        System.out.printf(
                "STRESS attempted=%d committed=%d rejected=%d concurrency=%d pool=%d seed=%d"
                        + " elapsed=%dms attempted/s=%.0f committed/s=%.0f%n",
                TRANSFERS,
                committed.get(),
                rejected.get(),
                CONCURRENCY,
                poolSize,
                SEED,
                elapsedMillis,
                TRANSFERS / seconds,
                committed.get() / seconds);
    }
}
