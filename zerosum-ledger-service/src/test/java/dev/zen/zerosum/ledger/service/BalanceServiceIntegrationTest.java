package dev.zen.zerosum.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.support.AbstractIntegrationTest;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BalanceServiceIntegrationTest extends AbstractIntegrationTest {
    @Autowired TransferService transferService;
    @Autowired BalanceService balanceService;

    @Test
    void balanceIsCreditsMinusDebits() {
        Account cash = newAccount("CASH", "USD", AccountType.ASSET);
        Account sales = newAccount("SALES", "USD", AccountType.REVENUE);
        Account rent = newAccount("RENT", "USD", AccountType.EXPENSE);

        move(cash, sales, 5000, "USD"); // cash debited, sales credited
        move(rent, cash, 1200, "USD"); // rent debited, cash credited

        assertThat(balanceService.balanceOf(cash.getId())).isEqualTo(Money.of(-3800, "USD"));
        assertThat(balanceService.balanceOf(sales.getId())).isEqualTo(Money.of(5000, "USD"));
        assertThat(balanceService.balanceOf(rent.getId())).isEqualTo(Money.of(-1200, "USD"));
    }

    @Test
    void anAccountWithNoEntriesHasZeroBalanceInItsOwnCurrency() {
        Account idle = newAccount("IDLE", "EUR", AccountType.ASSET);

        assertThat(balanceService.balanceOf(idle.getId())).isEqualTo(Money.of(0, "EUR"));
    }

    @Test
    void anUnknownAccountIsRejected() {
        assertThatThrownBy(() -> balanceService.balanceOf(UUID.randomUUID()))
                .isInstanceOf(LedgerException.AccountNotFound.class);
    }

    @Test
    void trialBalanceIsEmptyForAnEmptyLedger() {
        assertThat(balanceService.trialBalance()).isEmpty();
    }

    @Test
    void trialBalanceNetsToZeroPerCurrency() {
        Account usdA = newAccount("USD-A", "USD", AccountType.ASSET);
        Account usdB = newAccount("USD-B", "USD", AccountType.REVENUE);
        Account eurA = newAccount("EUR-A", "EUR", AccountType.ASSET);
        Account eurB = newAccount("EUR-B", "EUR", AccountType.REVENUE);

        move(usdA, usdB, 7777, "USD");
        move(eurA, eurB, 123, "EUR");

        assertThat(balanceService.trialBalance())
                .containsOnlyKeys(Currency.getInstance("USD"), Currency.getInstance("EUR"))
                .allSatisfy((currency, total) -> assertThat(total).isZero());
    }

    private void move(Account debit, Account credit, long amount, String currency) {
        transferService.post(
                new PostTransferCommand(
                        "ref",
                        "test",
                        Currency.getInstance(currency),
                        List.of(
                                PostTransferCommand.debit(
                                        debit.getId(), Money.of(amount, currency)),
                                PostTransferCommand.credit(
                                        credit.getId(), Money.of(amount, currency)))));
    }
}
