package nl.banksim.domain.rente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import nl.banksim.domain.geld.Money;

class RenteCalculatorTests {

    private final RenteCalculator calculator = RenteCalculator.standaard();

    @Test
    void renteOverEenMaandMetVastSaldo() {
        // 12.000 × 3% / 365 × 31 dagen = 30,5753… → 30,58
        Money rente = calculator.renteVoorMaand(YearMonth.of(2025, 10), dag -> Money.of("12000.00"));
        assertThat(rente).isEqualTo(Money.of("30.58"));
    }

    @Test
    void schrikkeljaarTeltMet366Dagen() {
        BigDecimal schrikkel = calculator.dagrente(Money.of("36600.00"), LocalDate.of(2024, 2, 29));
        BigDecimal normaal = calculator.dagrente(Money.of("36500.00"), LocalDate.of(2025, 3, 1));
        assertThat(schrikkel).isEqualByComparingTo("3.0000000000");
        assertThat(normaal).isEqualByComparingTo("3.0000000000");
    }

    @Test
    void geenRenteOverNulOfNegatiefSaldo() {
        assertThat(calculator.dagrente(Money.ZERO, LocalDate.of(2026, 1, 1))).isZero();
        assertThat(calculator.dagrente(Money.of("-10.00"), LocalDate.of(2026, 1, 1))).isZero();
    }

    @Test
    void saldoWijzigtBinnenDeMaand() {
        // 1 t/m 15 juni 1.000, 16 t/m 30 juni 2.000 → (15 × 1.000 + 15 × 2.000) × 0,03 / 365 = 3,6986… → 3,70
        Money rente = calculator.renteVoorMaand(YearMonth.of(2026, 6),
                dag -> dag.getDayOfMonth() <= 15 ? Money.of("1000.00") : Money.of("2000.00"));
        assertThat(rente).isEqualTo(Money.of("3.70"));
    }

    @Test
    void opgebouwdeRenteTotEenDag() {
        BigDecimal opgebouwd = calculator.opgebouwd(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                dag -> Money.of("12405.63"));
        assertThat(Money.afgerond(opgebouwd)).isEqualTo(Money.of("5.10"));
    }

    @Test
    void negatiefPercentageIsOngeldig() {
        assertThatThrownBy(() -> new RenteCalculator(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nulProcentIsToegestaan() {
        assertThat(new RenteCalculator(BigDecimal.ZERO).renteVoorMaand(YearMonth.of(2026, 1), dag -> Money.of("100.00")))
                .isEqualTo(Money.ZERO);
    }
}
