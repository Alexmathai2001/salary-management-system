package com.acme.salary.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pure unit tests of the USD conversion formula. */
class FxConverterTest {

    @Test
    void usdIsUnchanged() {
        assertThat(FxConverter.toUsdCents(8_500_000, 1_000_000, 2)).isEqualTo(8_500_000);
    }

    @Test
    void convertsRupeesUsingThePaiseMinorUnit() {
        // 8,300,000 INR = 8,300,000 / 83 = 100,000 USD = 10,000,000 cents. Salary stored as paise.
        assertThat(FxConverter.toUsdCents(830_000_000L, 83_000_000L, 2)).isEqualTo(10_000_000L);
    }

    @Test
    void convertsYenWhichHasNoMinorUnit() {
        // 15,000,000 JPY = 100,000 USD at 150 JPY/USD. Salary is stored as whole yen (fraction digits 0).
        assertThat(FxConverter.toUsdCents(15_000_000L, 150_000_000L, 0)).isEqualTo(10_000_000L);
    }

    @Test
    void convertsFromAStrongerCurrency() {
        // 79,000 GBP at 0.79 GBP/USD = 100,000 USD.
        assertThat(FxConverter.toUsdCents(7_900_000L, 790_000L, 2)).isEqualTo(10_000_000L);
    }

    @Test
    void roundsToTheNearestCent() {
        // 1 INR cent-equivalent: 100 paise / 83 = 1.2048 USD cents -> 1
        assertThat(FxConverter.toUsdCents(100, 83_000_000L, 2)).isEqualTo(1);
        // 105 paise / 83 = 1.2651 -> 1; 125 paise / 83 = 1.506 -> 2
        assertThat(FxConverter.toUsdCents(125, 83_000_000L, 2)).isEqualTo(2);
    }

    @Test
    void rejectsNonPositiveRates() {
        assertThatThrownBy(() -> FxConverter.toUsdCents(100, 0, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
