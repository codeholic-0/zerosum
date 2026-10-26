package dev.zen.zerosum.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.service.BalanceService;
import dev.zen.zerosum.ledger.service.PostTransferCommand;
import dev.zen.zerosum.ledger.service.TransferService;
import dev.zen.zerosum.ledger.support.AbstractIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LedgerInvariantRandomizedIntegrationTest extends AbstractIntegrationTest {
    private static final long DEFAULT_SEED = 20261026L;
    private static final int TRANSFERS = 1_000;
    private static final int ACCOUNTS_PER_CURRENCY = 6;
    private static final List<String> CURRENCIES = List.of("USD", "EUR", "JPY");

    @Autowired TransferService transferService;
    @Autowired BalanceService balanceService;

    @Test
    void randomValidTransfersKeepEveryTransferBalancedAndTheLedgerAtZero() {
        long seed = Long.getLong("zerosum.seed", DEFAULT_SEED);
        System.out.println("Randomized invariant test seed = " + seed);
        SplittableRandom random = new SplittableRandom(seed);

        Map<String, List<Account>> accountsByCurrency = new LinkedHashMap<>();
        for (String code : CURRENCIES) {
            List<Account> accounts = new ArrayList<>();
            for (int i = 0; i < ACCOUNTS_PER_CURRENCY; i++) {
                accounts.add(newAccount(code + "-" + i, code, AccountType.ASSET));
            }
            accountsByCurrency.put(code, accounts);
        }

        // Independent model of what every balance should be.
        Map<UUID, Long> expected = new HashMap<>();

        for (int n = 0; n < TRANSFERS; n++) {
            String code = CURRENCIES.get(random.nextInt(CURRENCIES.size()));
            List<Account> pool = new ArrayList<>(accountsByCurrency.get(code));
            Collections.shuffle(pool, random);

            int debitCount = 1 + random.nextInt(3);
            int creditCount = 1 + random.nextInt(3);
            List<PostTransferCommand.Leg> legs = new ArrayList<>();

            long debitTotal = 0;
            for (int i = 0; i < debitCount; i++) {
                long amount = 3 + random.nextLong(100_000);
                debitTotal += amount;
                Account account = pool.get(i);
                legs.add(PostTransferCommand.debit(account.getId(), Money.of(amount, code)));
                expected.merge(account.getId(), -amount, Long::sum);
            }

            long[] creditAmounts = split(debitTotal, creditCount, random);
            for (int i = 0; i < creditCount; i++) {
                Account account = pool.get(debitCount + i);
                legs.add(
                        PostTransferCommand.credit(
                                account.getId(), Money.of(creditAmounts[i], code)));
                expected.merge(account.getId(), creditAmounts[i], Long::sum);
            }

            transferService.post(
                    new PostTransferCommand(
                            "rand-" + n, "randomized", Currency.getInstance(code), legs));
        }

        String context = "seed=" + seed;

        assertThat(count("transfers")).as(context).isEqualTo(TRANSFERS);

        // Invariant 1: every transfer balances on its own.
        List<UUID> unbalanced =
                jdbc.queryForList(
                        """
                select transfer_id from journal_entries
                group by transfer_id
                having sum(case when direction = 'CREDIT'
                                then amount_minor else -amount_minor end) <> 0
                """,
                        UUID.class);
        assertThat(unbalanced).as(context).isEmpty();

        // Invariant 2: the whole ledger nets to zero in every currency.
        assertThat(balanceService.trialBalance())
                .as(context)
                .hasSize(CURRENCIES.size())
                .allSatisfy((currency, total) -> assertThat(total).isZero());

        // Invariant 3: every derived balance matches the independent model.
        for (List<Account> accounts : accountsByCurrency.values()) {
            for (Account account : accounts) {
                assertThat(balanceService.balanceOf(account.getId()).amountMinor())
                        .as("%s account %s", context, account.getCode())
                        .isEqualTo(expected.getOrDefault(account.getId(), 0L));
            }
        }
    }

    /**
     * Splits total into `parts` positive amounts that sum exactly to total. Needs total >= parts.
     */
    private static long[] split(long total, int parts, SplittableRandom random) {
        if (parts == 1) {
            return new long[] {total};
        }
        TreeSet<Long> cuts = new TreeSet<>();
        while (cuts.size() < parts - 1) {
            cuts.add(1 + random.nextLong(total - 1));
        }
        long[] result = new long[parts];
        long previous = 0;
        int i = 0;
        for (long cut : cuts) {
            result[i++] = cut - previous;
            previous = cut;
        }
        result[i] = total - previous;
        return result;
    }
}
