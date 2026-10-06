package nl.banksim.domain.geld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTests {

    @Test
    void heeftAltijdTweeDecimalen() {
        assertThat(Money.of("12").toString()).isEqualTo("12.00");
        assertThat(Money.of("12.5").toString()).isEqualTo("12.50");
        assertThat(Money.of(new BigDecimal("1E+3")).toString()).isEqualTo("1000.00");
    }

    @Test
    void weigertMeerDanTweeDecimalen() {
        assertThatThrownBy(() -> Money.of("0.001")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Money.of("0.100")).isEqualTo(Money.of("0.10"));
    }

    @Test
    void weigertOngeldigeTekst() {
        assertThatThrownBy(() -> Money.of("12,50")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12,50");
    }

    @Test
    void geenAfrondingsfoutenZoalsBijDouble() {
        Money totaal = Money.ZERO;
        for (int i = 0; i < 10; i++) {
            totaal = totaal.plus(Money.of("0.10"));
        }
        assertThat(totaal).isEqualTo(Money.of("1.00"));
    }

    @Test
    void rondtAfMetBankiersafronding() {
        assertThat(Money.afgerond(new BigDecimal("2.345"))).isEqualTo(Money.of("2.34"));
        assertThat(Money.afgerond(new BigDecimal("2.355"))).isEqualTo(Money.of("2.36"));
        assertThat(Money.afgerond(new BigDecimal("-2.345"))).isEqualTo(Money.of("-2.34"));
    }

    @Test
    void rekenkunde() {
        Money a = Money.of("100.00");
        Money b = Money.of("63.48");
        assertThat(a.minus(b)).isEqualTo(Money.of("36.52"));
        assertThat(b.negate()).isEqualTo(Money.of("-63.48"));
        assertThat(b.negate().abs()).isEqualTo(b);
        assertThat(b.negate().isNegative()).isTrue();
        assertThat(b.isPositive()).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(a.isGreaterThan(b)).isTrue();
        assertThat(b.isLessThan(a)).isTrue();
        assertThat(Money.min(a, b)).isEqualTo(b);
    }

    @Test
    void vergelijkingenBijGelijkeBedragen() {
        Money a = Money.of("10.00");
        assertThat(a.isGreaterThan(Money.of("10.00"))).isFalse();
        assertThat(a.isLessThan(Money.of("10.00"))).isFalse();
        assertThat(a.isGreaterThan(Money.of("10.01"))).isFalse();
        assertThat(a.isLessThan(Money.of("9.99"))).isFalse();
        assertThat(Money.min(a, Money.of("10.00"))).isSameAs(a);
    }
}
