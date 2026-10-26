package dev.zen.zerosum.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void plusAndMinusWorkWithinOneCurrency() {
        Money a = Money.of(500, "USD");
        Money b = Money.of(200, "USD");

        assertThat(a.plus(b)).isEqualTo(Money.of(700, "USD"));
        assertThat(a.minus(b)).isEqualTo(Money.of(300, "USD"));
        assertThat(b.minus(a)).isEqualTo(Money.of(-300, "USD"));
    }

    @Test
    void mixingCurrenciesIsRejected() {
        Money usd = Money.of(100, "USD");
        Money eur = Money.of(100, "EUR");

        assertThatThrownBy(() -> usd.plus(eur)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usd.minus(eur)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowFailsInsteadOfWrappingAround() {
        Money max = Money.of(Long.MAX_VALUE, "USD");
        Money min = Money.of(Long.MIN_VALUE, "USD");

        assertThatThrownBy(() -> max.plus(Money.of(1, "USD")))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> min.minus(Money.of(1, "USD")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void invalidCurrencyCodesAreRejected() {
        assertThatThrownBy(() -> Money.of(100, "ABC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ABC");
    }

    @Test
    void positiveAndZeroChecks() {
        assertThat(Money.of(1, "USD").isPositive()).isTrue();
        assertThat(Money.of(0, "USD").isPositive()).isFalse();
        assertThat(Money.of(-1, "USD").isPositive()).isFalse();
        assertThat(Money.of(0, "USD").isZero()).isTrue();
    }

    @Test
    void equalityIsByAmountAndCurrency() {
        assertThat(Money.of(100, "USD")).isEqualTo(Money.of(100, "USD"));
        assertThat(Money.of(100, "USD")).isNotEqualTo(Money.of(100, "EUR"));
        assertThat(Money.of(100, "USD")).isNotEqualTo(Money.of(101, "USD"));
    }
}
