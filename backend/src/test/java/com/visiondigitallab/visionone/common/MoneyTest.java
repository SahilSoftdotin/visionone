package com.visiondigitallab.visionone.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic, pinned down. This is where client trust is lost.
 */
class MoneyTest {

    @Test
    @DisplayName("cost per lead with no leads is null, not zero and not infinity")
    void divisionByZeroYieldsNull() {
        assertThat(Money.of(500_000L, "USD").dividedBy(0L)).isNull();
    }

    @Test
    @DisplayName("cost per lead rounds half up to the nearest cent")
    void divisionRoundsHalfUp() {
        // $100.00 over 3 leads = $33.333... -> 3333 cents
        assertThat(Money.of(10_000L, "USD").dividedBy(3L)).isEqualTo(Money.of(3_333L, "USD"));
        // $100.00 over 8 leads = $12.50 exactly
        assertThat(Money.of(10_000L, "USD").dividedBy(8L)).isEqualTo(Money.of(1_250L, "USD"));
        // $0.05 over 2 = $0.025 -> rounds up to 3 cents
        assertThat(Money.of(5L, "USD").dividedBy(2L)).isEqualTo(Money.of(3L, "USD"));
    }

    @Test
    @DisplayName("no cent is lost to floating point across a full budget")
    void additionIsExact() {
        Money total = Money.zero("USD");
        for (int i = 0; i < 1000; i++) {
            total = total.plus(Money.of(1L, "USD"));
        }
        assertThat(total.amountMinor()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("remaining budget can be negative when spend overruns the plan")
    void subtractionAllowsOverspend() {
        assertThat(Money.of(500_000L, "USD").minus(Money.of(520_000L, "USD")).amountMinor())
                .isEqualTo(-20_000L);
    }

    @Test
    @DisplayName("mixing currencies is refused rather than silently summed")
    void mixingCurrenciesFails() {
        assertThatThrownBy(() -> Money.of(100L, "USD").plus(Money.of(100L, "CAD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("CAD");
    }
}
